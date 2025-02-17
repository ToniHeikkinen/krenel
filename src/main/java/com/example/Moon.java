package com.example;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import static org.lwjgl.opengl.GL11.*;

public class Moon {
    private static final double R = 6.371e6; // radius
    private final double G = 6.67430e-11; // gravitational constant
    private final double M = 5.972e24; // mass
    private static final int ORBITSTEPS = 50000;
    private static final double SCALE = 1e-6; // scale factor for rendering

    private double[] position;
    private double[] velocity;

    private Queue<double[]> positionHistory;
    private Queue<double[]> velocityHistory;

    public Moon(double x0, double y0, double z0, double vx0, double vy0, double vz0){
        position = new double[]{x0, y0, z0};
        velocity = new double[]{vx0, vy0, vz0};

        positionHistory = new ArrayDeque<>();
        velocityHistory = new ArrayDeque<>();

        positionHistory.offer(position.clone());
        velocityHistory.offer(velocity.clone());
    }

    private double[] acceleration(double[] pos){
        double r = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        double factor = -G * M / (r * r * r);
        return new double[]{factor * pos[0], factor * pos[1], factor * pos[2]};
    }

    public void rk4Step(double dt){
        double[] k1v = acceleration(position);
        double[] k1x = velocity;

        double[] k2v = acceleration(offsetPosition(k1x, 0.5 * dt));
        double[] k2x = offsetVelocity(k1v, 0.5 * dt);

        double[] k3v = acceleration(offsetPosition(k2x, 0.5 * dt));
        double[] k3x = offsetVelocity(k2v, 0.5 * dt);

        double[] k4v = acceleration(offsetPosition(k3x, dt));
        double[] k4x = offsetVelocity(k3v, dt);

        for(int i = 0; i < 3; i++){
            position[i] += dt / 6.0 * (k1x[i] + 2 * k2x[i] + 2 * k3x[i] + k4x[i]);
            velocity[i] += dt / 6.0 * (k1v[i] + 2 * k2v[i] + 2 * k3v[i] + k4v[i]);
        }

        positionHistory.offer(position.clone());
        velocityHistory.offer(velocity.clone());

        while(positionHistory.size() > ORBITSTEPS){
            positionHistory.poll();
        }
        while(velocityHistory.size() > ORBITSTEPS){
            velocityHistory.poll();
        }
    }

    private double[] offsetPosition(double[] vec, double scale){
        return new double[]{position[0] + vec[0] * scale, position[1] + vec[1] * scale, position[2] + vec[2] * scale};
    }

    private double[] offsetVelocity(double[] vec, double scale){
        return new double[]{velocity[0] + vec[0] * scale, velocity[1] + vec[1] * scale, velocity[2] + vec[2] * scale};
    }

    public Queue<double[]> getPositionHistory(){
        return positionHistory;
    }

    public double getX(){
        return positionHistory.peek()[0];
    }

    public double getY(){
        return positionHistory.peek()[1];
    }

    public double getZ(){
        return positionHistory.peek()[2];
    }

    public double[] getVelocity(){
        return velocity;
    }

    public void setVelocity(double[] vel){
        velocity = vel;
    }

    public double[] getPosition(){
        return position;
    }

    public void setPosition(double[] pos){
        position = pos;
    }

    public void renderMoonTrajectory(){
        glColor3f(0.1f, 0.7f, 0.2f);
        glBegin(GL_LINE_STRIP);

        Queue<double[]> history = positionHistory;
        int elementsToRender = Math.min(history.size(), ORBITSTEPS);
        Iterator<double[]> iterator = history.iterator();

        for(int i = 0; i < history.size() - elementsToRender; i++){
            iterator.next();
        }

        while(iterator.hasNext()){
            double[] pos = iterator.next();
            glVertex3d(pos[0] * SCALE, pos[1] * SCALE, pos[2] * SCALE);
        }

        glEnd();
    }

    public void renderMoon(){
        glColor3f(0.0f, 1.0f, 0.0f);
        glPointSize(5.0f);

        Queue<double[]> history = positionHistory;

        if(!history.isEmpty()){
            double[] lastPosition = history.peek();

            if(lastPosition.length >= 3){
                glBegin(GL_POINTS);
                glVertex3d(lastPosition[0] * SCALE, lastPosition[1] * SCALE, lastPosition[2] * SCALE);
                glEnd();
            }
        }
        glEnd();
    }

    public void renderEarth() {
        glColor3f(0.2f, 1.0f, 0.0f);
        int slices = 8;
        int stacks = 8;
        double radius = R * SCALE;

        for(int i = 0; i <= stacks; i++){
            double lat0 = Math.PI * (-0.5 + (double) (i - 1) / stacks);
            double z0 = Math.sin(lat0) * radius;
            double zr0 = Math.cos(lat0) * radius;

            double lat1 = Math.PI * (-0.5 + (double) i / stacks);
            double z1 = Math.sin(lat1) * radius;
            double zr1 = Math.cos(lat1) * radius;

            glBegin(GL_LINE_LOOP);
            for(int j = 0; j <= slices; j++){
                double lng = 2 * Math.PI * (double) j / slices;
                double x = Math.cos(lng);
                double y = Math.sin(lng);

                glVertex3d(x * zr0, y * zr0, z0);
                glVertex3d(x * zr1, y * zr1, z1);
            }
            glEnd();
        }
    }
}
