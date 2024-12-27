package com.example;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImString;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;

import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.glfw.*;

public class Main {
    private long window;
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private static final int FPS = 150;
    private static final double FRAMETIME = 1.0 / FPS;
    private static final double EVENTDISTANCE = 1737400 + 2e8; // triggers the first event from this distance
    private static final double EVENTDISTANCE2 = 1737400 + 1e8; // ...

    private ImDouble input1 = new ImDouble(0.1);
    private ImDouble input2 = new ImDouble(0.1);
    private ImDouble input3 = new ImDouble(0.1);
    private ImString textBox = new ImString("Welcome recruit. Due to recent events, you have been assigned to manually" +
            " pilot this vessel to an orbit around the moon. All of our sensor stations are down and we have lost contact with two of our" +
            " moon bases. We are suspecting an act of sabotage or terrorism, hence we cannot trust autopilots for now." +
            " This vessel is equipped with a cannon and some basic thermal sensors which are powered via a 100MWth nuclear reactor. And the cargo" +
            " includes military personnel and nuclear missiles. It is your time to prove you're worthy of being in the USSF.");
    private ImString monitorText = new ImString("Incoming stream");

    private Rocket rocket;
    private Moon moon;
    private TextureManager textureManager;

    private boolean renderPlanned = false;
    private boolean paused = true;
    private boolean paused2 = true;
    private boolean paused3 = true;
    private boolean drawUSSF = true;
    private boolean totalPause = true;
    private boolean flashUSSF = false;
    private boolean conclude = false;
    private boolean renderEvent = false;
    private boolean wonEvent = false;
    private boolean wonEvent2 = false;

    private ImBoolean checkboxState = new ImBoolean(false);
    private ImBoolean checkboxState2 = new ImBoolean(false);
    private ImBoolean checkboxState3 = new ImBoolean(false);

    private static final double G = 6.67430e-11; // gravitational constant (m^3 kg^-1 s^-2)
    private static final double M = 5.972e24; // mass of the Earth (kg)
    private static final double R = 6.371e6; // radius of the Earth (m)
    private static final double M2 = 7.340e22; // mass of the Moon (kg)
    private static final double R2 = 1.740e6; // radius of the moon (m)
    private static final double SCALE = 1e-6; // Scale factor for rendering
    private static final double dt = 41; // time step (seconds)
    private static final int ORBITSTEPS = 50000;
    private int HACK = 0; // fixed a major bug with this, slightly inefficient to recalculate the orbit every 10000 steps and then not plot the last
                          // 10000 steps due to the fact that the moons position always resets after ORBITSTEPS.
                          // I tried multiple times in different ways to give moon a second trajectory etc but it just would not work the same way
                          // the rockets second histories etc would. Spent too much time on trying to optimize it perfectly. using this hack compared to
                          // recalculating 50000 steps in the orbit every time passTime is called is better though. The moons position still gets reset but
                          // this way it gets recalculated and pushed back every 10000 steps, while also not rendering the 10000 step trajectory when the moons
                          // position suddenly changes. The main culprit of all of this is the showChangedOrbit()-method, specifically having to always set the
                          // moons calculated values back to how they were before the loop. May be a trivial fix but i am sick of looking at this and its up to
                          // 100/1000/10000 times faster than the earlier way i had it. In theory for every 50000 steps it should require 50000 iterations, but
                          // it currently is around 250000, and it shows only shows 40000 steps of the orbit. Worst case scenario used to be 50000^2 steps...
    private static int TIME = 0;
    private int orbitTime = 0;
    // TODO: optimization -> stop the moons orbit from rendering more than 1 loop, since it is stationary. better yet not have it calculated at all, major code overhaul not worth it
    private double cameraDistance = 300.0;
    private double cameraAngleX = 30.0;
    private double cameraAngleY = 30.0;
    private double lastX = -1.0;
    private double lastY = -1.0;
    private float zFarRender = 1000000.0f;

    private final int screenWidth = 1920; // Width of the window
    private final int screenHeight = 1080; // Height of the window
    // TODO: add immersion to the USSF events, edge cases etc
    // Must be manually found and set to agree with the overlayed image:
    private final int renderHeightOffset = 84;
    private final int renderWidthOffset = 344;
    private final int renderWidth = 1108;
    private final int renderHeight = 765;

    // 3D rendered screen and cursorPosCallBack variables
    private final int boxX = (screenWidth / 2) - (renderWidth / 2) + renderWidthOffset;
    private final int boxY = (screenHeight / 2) - (renderHeight / 2) + renderHeightOffset;
    private final int boxWidth = renderWidth;
    private final int boxHeight = renderHeight;

    private final Map<String, Integer> textures = new HashMap<>();
    private double frameTime;
    private double frameTimeSum;
    float randomX = -1000;
    float randomY = -1000;
    float randomX2 = -1000;
    float randomY2 = -1000;

    public void run(){
        init();
        ImGui.createContext();
        imGuiGlfw.init(window,true);
        imGuiGl3.init("#version 130"); // MAJOR = 3, MINOR = 0 (1.3.0)
        loop();

        glfwFreeCallbacks(window); // deletes everything if the window closes, may not be necessary
        glfwDestroyWindow(window);

        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }

    private void init(){
        GLFWErrorCallback.createPrint(System.err).set();

        if(!glfwInit()){
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); // MAJOR = 3, MINOR = 0 (1.3.0)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        long monitor = glfwGetPrimaryMonitor();
        window = glfwCreateWindow(screenWidth, screenHeight, "krenel", monitor, NULL);
        if(window == NULL){
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // V-SYNC !!!, true/false
        glfwShowWindow(window);

        GL.createCapabilities();
        glClearColor(0.0f, 0.3f, 0.05f, 1.0f);
        glEnable(GL_DEPTH_TEST);

        double r_orbit = R + 200000;
        double v_orbit = Math.sqrt(G * M / r_orbit);

        textureManager = new TextureManager(textures, screenWidth, screenHeight);

        rocket = new Rocket(r_orbit, 0, 0, 0, v_orbit, 0);
        moon = new Moon(370000000, 0, 37000000, 0, 1000, 0);

        glfwSetCursorPosCallback(window, this::cursorPosCallback);
        glfwSetScrollCallback(window, this::scrollCallback);
    }

    private void loop(){
        for(int i = 1; i <= ORBITSTEPS; i++){
            moon.rk4Step(dt);
        }
        showChangedOrbit(); // orbit initializations
        applyChangedOrbit();
        textureManager.loadTexture("overlay", "/finalOverLay.png"); // hashmap for  textures
        textureManager.loadTexture("USSF2", "/USSF2.png");
        textureManager.loadTexture("USSFglitched", "/USSFglitched.png");

        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.0f, 0.0f, 0.0f, 0.0f); // background color for imgui (transparent)
        while( !glfwWindowShouldClose(window) ){
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            double startTime = glfwGetTime();
            textureManager.drawOverlay("overlay", screenWidth, screenHeight, 0, 0);

            if(flashUSSF){ // USSF display logic
                textureManager.drawOverlay("USSFglitched", 650, 764, 63, 73);
                flashUSSF = false;
            }else{ // only run the flash or draw for a given frame, not both at the same time
                if(drawUSSF){
                    textureManager.drawOverlay("USSF2", 650, 764, 63, 73);
                }
            }
            // distance between moon and earth to trigger an event
            if( (Math.sqrt(Math.pow(rocket.getX()-moon.getX(),2) // text and bools redundantly assigned each time
                    +Math.pow(rocket.getY()-moon.getY(),2)
                    +Math.pow(rocket.getZ()-moon.getZ(),2))) < EVENTDISTANCE && !renderEvent && !wonEvent ){
                flashUSSF = true;
                drawUSSF = true;
                conclude = false;
                totalPause = true; // pause everything
                renderEvent = true; // starts the fighting event
                textBox = new ImString(
                        "We've detected a thermal signal at a distance of 752 kilometers." +
                                " We are not sure if this is from a civilian cargo ship or a rogue military ship," +
                                " but given the current situation, we cannot take any chances. " +
                                "Open fire immediately, that is an order."
                );
            }

            if(conclude){
                render2DCrosshair(63, 73, 650, 764);
                if(renderEvent){
                    totalPause = true; // TODO: text and bools redundantly assigned each time, performance impact = ???
                    renderEvent(63, 73, 650, 764);
                    monitorText = new ImString("Artillery view: thermal signature detected");
                }else{
                    monitorText = new ImString("Artillery view: no thermal signatures detected");
                    totalPause = false;
                }
                if((Math.sqrt(Math.pow(rocket.getX()-moon.getX(),2)
                        +Math.pow(rocket.getY()-moon.getY(),2)
                        +Math.pow(rocket.getZ()-moon.getZ(),2))) < EVENTDISTANCE2 && orbitTime >= 500000 && !wonEvent2){ // make sure you orbit the moon for long enough
                    flashUSSF = true;
                    drawUSSF = true;
                    wonEvent2 = true;
                    renderEvent = false;
                    monitorText = new ImString("Incoming stream");
                    textBox = new ImString(
                            "Good job, we'll take it from here."
                    );
                }
            }

            if(HACK >= 10000){
                showChangedOrbit();
                applyChangedOrbit();
                HACK = 0;
            }

            if(!totalPause){ // orbit timewarp
                passTime(paused,1);
                passTime(paused2,10);
                passTime(paused3,100);
            }

            // separate imgui rendering
            imGuiGlfw.newFrame();
            ImGui.newFrame();

            renderImGuiTextBox();
            renderImGuiOrbitEditor();
            renderImGuiCannonScreen();

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());
            // OpenGL related UIs and renders
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();

            // FPS limiter
            double endTime = glfwGetTime();
            frameTime = endTime - startTime;
            // Sleep for the remaining time to maintain target frame rate
            if(frameTime < FRAMETIME){ // if frame was ran faster than how long it shouldve taken
                try{
                    Thread.sleep((long)((FRAMETIME - frameTime) * 1000)); // milliseconds
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void passTime(boolean paused, int warpSteps){
        if(!paused){
            for(int i = 1; i <= warpSteps; i++){
                moon.rk4Step(dt);
                rocket.rk4Step(dt, false, moon);
                HACK++;
            }
            rocket.clearPosition2();
            rocket.clearVelocity2();
            rocket.clearPositionHistory2();
            rocket.clearVelocityHistory2();

            TIME = (int) (TIME + dt * warpSteps);
            // TODO: add second event "incoming stream"
            if((Math.sqrt(Math.pow(rocket.getX()-moon.getX(),2) // for the final event, only calculated if within range
                    +Math.pow(rocket.getY()-moon.getY(),2)
                    +Math.pow(rocket.getZ()-moon.getZ(),2))) < EVENTDISTANCE2){
                orbitTime = (int) (orbitTime + dt * warpSteps);
            }
        }
    }

    private void showChangedOrbit(){
        double changedVX = rocket.getVX() + input1.get();
        double changedVY = rocket.getVY() + input2.get();
        double changedVZ = rocket.getVZ() + input3.get();
        double[] position2 = new double[]{rocket.getX(), rocket.getY(), rocket.getZ()};
        double[] velocity2 = new double[]{changedVX, changedVY, changedVZ};

        rocket.addPositionAndVelocity(position2, velocity2);

        double[] moonSavedPosition = Arrays.copyOf(moon.getPosition(), moon.getPosition().length);
        double[] moonSavedVelocity = Arrays.copyOf(moon.getVelocity(), moon.getVelocity().length);

        for(int i = 1; i <= ORBITSTEPS; i++){
            moon.rk4Step(dt);
            rocket.rk4Step(dt, true, moon);
        }
        // this is mandatory but also causes the issue fixed with HACK
        moon.setPosition(moonSavedPosition);
        moon.setVelocity(moonSavedVelocity);

        renderPlanned = true;
    }

    private void applyChangedOrbit(){ // Clears the first history and inserts the second history into the first one
        if(input1.get() + input2.get() + input3.get() != 0.0){
            rocket.clearPositionHistory();
            rocket.clearVelocityHistory();
            rocket.clearPosition();
            rocket.clearVelocity();
            rocket.setPositionHistory(new ArrayDeque<>(rocket.getPositionHistory2()));
            rocket.setVelocityHistory(new ArrayDeque<>(rocket.getVelocityHistory2()));

            renderPlanned = false;
            input1.set(0.0);
            input2.set(0.0);
            input3.set(0.0);
        }
    }

    private void renderImGuiTextBox(){
        ImGui.setNextWindowPos(63, 870, ImGuiCond.Once);
        ImGui.setNextWindowSize(650, 180, ImGuiCond.Once);
        int windowFlags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse;
        ImGui.begin("Incoming notifications", windowFlags);
        ImGui.text(">Communications panel");
        ImGui.textWrapped(String.valueOf(textBox));
        if(ImGui.button("Conclude intermission") && !conclude){
            flashUSSF = true;
            totalPause = false;
            drawUSSF = false;
            conclude = true;
            monitorText = new ImString("Artillery view: No thermal sights");
        }
        ImGui.end();
    }

    private void renderImGuiCannonScreen(){
        ImGui.setNextWindowPos(63, 74, ImGuiCond.Once);
        ImGui.setNextWindowSize(650, 764, ImGuiCond.Once);
        int windowFlags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse;
        ImGui.begin("Thermal signature", windowFlags);
        ImGui.text(String.valueOf(monitorText));
        ImGui.end();
    }

    private void renderImGuiOrbitEditor(){
        ImGui.setNextWindowPos(751, 870, ImGuiCond.Once);
        ImGui.setNextWindowSize(renderWidth - 3, 180, ImGuiCond.Once);

        int windowFlags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse;
        ImGui.begin("VELOCITY INPUT", windowFlags);
        ImGui.text(">Orbit editor: Integrator method: Classical Runge-Kutta");

        if(ImGui.checkbox("Timewarp 1x  ", checkboxState)){
            paused = !checkboxState.get();
        }
        ImGui.sameLine();
        ImGui.text("- Add Velocity X: ");
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        ImGui.inputDouble("vx: m/s:", input1);
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        ImGui.text("Current X velocity: " + rocket.getVX() + " m/s");

        if(ImGui.checkbox("Timewarp 10x ", checkboxState2)){
            paused2 = !checkboxState2.get();
        }
        ImGui.sameLine();
        ImGui.text("- Add Velocity Y: ");
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        ImGui.inputDouble("vy: m/s:", input2);
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        ImGui.text("Current Y velocity: " + rocket.getVY() + " m/s");

        if(ImGui.checkbox("Timewarp 100x", checkboxState3)){
            paused3 = !checkboxState3.get();
        }
        ImGui.sameLine();
        ImGui.text("- Add Velocity Z: ");
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        ImGui.inputDouble("vz: m/s:", input3);
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        ImGui.text("Current Z velocity: " + rocket.getVZ() + " m/s");

        ImGui.text("Total velocity: " + Math.sqrt( Math.pow(rocket.getVX(), 2) + Math.pow(rocket.getVY(), 2) + Math.pow(rocket.getVZ(), 2) ) + " m/s");
        ImGui.text("Total time passed: " + TIME + " s");

        if(ImGui.button("Show changed orbit")){
            showChangedOrbit();
        }
        if(ImGui.button("Apply changed orbit")){
            applyChangedOrbit();
        }

        ImGui.end();
    }

    private void render(){
        glViewport(boxX, boxY, boxWidth, boxHeight);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        OpenGLUtils.perspectiveGL(90.0f, (float) renderWidth / renderHeight, 0.1f, zFarRender);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        // calculate the camera position based on angles and distance
        double eyeX = rocket.getX() * SCALE + cameraDistance * Math.cos(Math.toRadians(cameraAngleX)) * Math.cos(Math.toRadians(cameraAngleY));
        double eyeY = rocket.getZ() * SCALE + cameraDistance * Math.cos(Math.toRadians(cameraAngleX)) * Math.sin(Math.toRadians(cameraAngleY));
        double eyeZ = rocket.getY() * SCALE + cameraDistance * Math.sin(Math.toRadians(cameraAngleX));

        // set the camera to look at the rocket's position
        OpenGLUtils.lookAtGL(eyeX, eyeY, eyeZ, rocket.getX() * SCALE, rocket.getY() * SCALE, rocket.getZ() * SCALE, 0.0, 0.0, 1.0);

        moon.renderEarth(); // moon class renders the earth too because why not
        moon.renderMoon();
        moon.renderMoonTrajectory();
        rocket.renderRocket();
        rocket.renderTrajectory();
        rocket.renderVectors(moon);
        if(renderPlanned){
            rocket.renderPlannedTrajectory();
        }
    }

    private void renderEvent(int overlayX, int overlayY, int overlayWidth, int overlayHeight){
        glViewport(0, 0, screenWidth, screenHeight);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, screenWidth, 0, screenHeight, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        float crosshairSize = 24.0f;

        float centerX = overlayX + overlayWidth / 2.0f;
        float centerY = screenHeight - (overlayY + overlayHeight / 2.0f);

        if(frameTime > FRAMETIME){ // if the calculated frameTime is longer than the preferred FRAMETIME
            frameTimeSum = frameTimeSum + frameTime;
        }else{ // if the frame takes less time to calculate than the preferred FRAMETIME
            frameTimeSum = frameTimeSum + FRAMETIME;
        }
        if(frameTimeSum >= 3){ // fire every ~3 seconds
            Random rand = new Random();
            randomX = -0.5f + rand.nextFloat();
            randomY = -0.5f + rand.nextFloat();
            frameTimeSum = 0;
            // 25% chance of hitting the middle square, TODO: make it follow a 2D normal probability distribution
            if((randomX2 >= -0.25 && randomX2 <= 0.25) && (randomY2 >= -0.25 && randomY2 <= 0.25)){ // TODO: no need for randomX2/Y2 variables, change if()-logic
                renderEvent = false; // stops the firing
                wonEvent = true; // fixes the initiation logic
                return; // stop from rendering the last shot
            }
        }

        float pointSize = 3.0f;
        float pointX = centerX + randomX * crosshairSize * 2;
        float pointY = centerY + randomY * crosshairSize * 2;
        randomX2 = randomX;
        randomY2 = randomY;

        glColor3f(0.2f, 1.0f, 0.0f);
        glBegin(GL_QUADS);
        glVertex2f(pointX - pointSize, pointY - pointSize);
        glVertex2f(pointX + pointSize, pointY - pointSize);
        glVertex2f(pointX + pointSize, pointY + pointSize);
        glVertex2f(pointX - pointSize, pointY + pointSize);
        glEnd();
    }

    private void render2DCrosshair(int overlayX, int overlayY, int overlayWidth, int overlayHeight){
        glViewport(0, 0, screenWidth, screenHeight);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, screenWidth, 0, screenHeight, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        float crosshairSize = 24.0f;
        float centerX = overlayX + overlayWidth / 2.0f;
        float centerY = screenHeight - (overlayY + overlayHeight / 2.0f);

        glColor3f(1.0f, 1.0f, 1.0f);
        glBegin(GL_LINES);
        glVertex2f(centerX - crosshairSize, centerY);
        glVertex2f(centerX + crosshairSize, centerY);
        glVertex2f(centerX, centerY - crosshairSize);
        glVertex2f(centerX, centerY + crosshairSize);
        glEnd();
    }

    private void cursorPosCallback(long window, double xpos, double ypos){
        if(glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS){
            if(lastX == -1.0 && lastY == -1.0){
                lastX = xpos;
                lastY = ypos;
            }

            double dx = xpos - lastX;
            double dy = ypos - lastY;

            if(xpos >= boxX && xpos <= (boxX + boxWidth) && screenHeight - ypos >= boxY && screenHeight - ypos <= (boxY + boxHeight)){
                cameraAngleY += dx * 0.1;
                cameraAngleX -= dy * 0.1;
            }

            lastX = xpos;
            lastY = ypos;
        }else{
            lastX = -1.0;
            lastY = -1.0;
        }
    }

    private void scrollCallback(long window, double xoffset, double yoffset){
        cameraDistance -= yoffset * 50.0;
        if (cameraDistance < 0.1) {
            cameraDistance = 0.1;
        }
    }

    public static void main(String[] args){
        new Main().run();
    }
}
