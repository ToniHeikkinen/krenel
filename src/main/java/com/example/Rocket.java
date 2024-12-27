package com.example;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import static org.lwjgl.opengl.GL11.*;

public class Rocket {
    private static final double G = 6.67430e-11; // gravitational constant
    private static final double M = 5.972e24; // mass of the earth
    private static final double R = 6.371e6; // radius of the earth
    private static final double M2 = 7.34e22; // mass of the moon
    private static final double R2 = 1.74e6; // radius of the moon
    private static final int ORBITSTEPS = 50000;
    private int HACK = 0;

    private double[] position;
    private double[] velocity;
    private double[] position2;
    private double[] velocity2;
    private Queue<double[]> positionHistory;
    private Queue<double[]> velocityHistory;
    private Queue<double[]> positionHistory2;
    private Queue<double[]> velocityHistory2;
    private static final double SCALE = 1e-6; // scale factor for rendering

    public Rocket(double x0, double y0, double z0, double vx0, double vy0, double vz0){
        position = new double[]{x0, y0, z0};
        velocity = new double[]{vx0, vy0, vz0};

        position2 = new double[]{x0, y0, z0};
        velocity2 = new double[]{vx0, vy0, vz0};

        positionHistory = new ArrayDeque<>();
        velocityHistory = new ArrayDeque<>();

        positionHistory2 = new ArrayDeque<>();
        velocityHistory2 = new ArrayDeque<>();

        positionHistory.offer(position.clone());
        velocityHistory.offer(velocity.clone());
    }

    private double[] acceleration(double[] pos, Moon moon){
        double[] oldestPosition = {0.0,0.0,0.0};
        HACK++;
        if (HACK >= ORBITSTEPS){
            oldestPosition = moon.getPosition();
            HACK = 0;
        }else if(HACK < ORBITSTEPS){
            Queue<double[]> positionHistory = moon.getPositionHistory();
            if (!positionHistory.isEmpty()){
                oldestPosition = positionHistory.peek();
            }
        }

        double disX = pos[0] - oldestPosition[0];
        double disY = pos[1] - oldestPosition[1];
        double disZ = pos[2] - oldestPosition[2];

        double r = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        double r2 = Math.sqrt(disX * disX + disY * disY + disZ * disZ);

        double a = -G * M / (r * r * r);
        double accX = a * pos[0];
        double accY = a * pos[1];
        double accZ = a * pos[2];

        double a2 = -G * M2 / (r2 * r2 * r2);
        double totX = a2 * disX;
        double totY = a2 * disY;
        double totZ = a2 * disZ;

        return new double[]{accX + totX, accY + totY, accZ + totZ};
    }

    public void rk4Step(double dt, boolean isOrbitPlanner, Moon moon){
        if(isOrbitPlanner){
            rungeKuttaStep(position2, velocity2, dt, moon, positionHistory2, velocityHistory2);
        }else{
            rungeKuttaStep(position, velocity, dt, moon, positionHistory, velocityHistory);
        }
    }

    private void rungeKuttaStep(double[] position, double[] velocity, double dt, Moon moon, Queue<double[]> positionHistory, Queue<double[]> velocityHistory){
        double[] k1v = acceleration(position, moon);
        double[] k1x = velocity;

        double[] k2v = acceleration(offsetPosition(position, k1x, 0.5 * dt), moon);
        double[] k2x = offsetVelocity(velocity, k1v, 0.5 * dt);

        double[] k3v = acceleration(offsetPosition(position, k2x, 0.5 * dt), moon);
        double[] k3x = offsetVelocity(velocity, k2v, 0.5 * dt);

        double[] k4v = acceleration(offsetPosition(position, k3x, dt), moon);
        double[] k4x = offsetVelocity(velocity, k3v, dt);

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

    private double[] offsetPosition(double[] position, double[] vec, double scale){
        return new double[]{position[0] + vec[0] * scale, position[1] + vec[1] * scale, position[2] + vec[2] * scale};
    }

    private double[] offsetVelocity(double[] velocity, double[] vec, double scale){
        return new double[]{velocity[0] + vec[0] * scale, velocity[1] + vec[1] * scale, velocity[2] + vec[2] * scale};
    }

    public void renderRocket(){
        glColor3f(0.0f, 1.0f, 0.5f);
        glPointSize(5.0f);

        glBegin(GL_POINTS);
        glVertex3d(this.getX() * SCALE, this.getY() * SCALE, this.getZ() * SCALE);
        glEnd();
    }

    public void renderTrajectory(){
        glColor3f(0.2f, 0.6f, 1.2f);
        glBegin(GL_LINE_STRIP);

        Queue<double[]> history = this.getPositionHistory();
        int totalSize = history.size();
        int elementsToSkip = 10000;
        int elementsToRender = Math.min(totalSize - elementsToSkip, ORBITSTEPS);

        Iterator<double[]> iterator = history.iterator();

        while(iterator.hasNext() && elementsToRender > 0){
            double[] pos = iterator.next();
            glVertex3d(pos[0] * SCALE, pos[1] * SCALE, pos[2] * SCALE);
            elementsToRender--;
        }

        glEnd();
    }

    public void renderPlannedTrajectory(){
        glColor3f(1.0f, 1.0f, 1.0f);
        glBegin(GL_LINE_STRIP);
        Queue<double[]> history2 = this.getPositionHistory2();

        int elementsToRender = Math.min(history2.size(), ORBITSTEPS);
        Iterator<double[]> iterator = history2.iterator();

        for(int i = 0; i < history2.size() - elementsToRender; i++){
            iterator.next();
        }

        while(iterator.hasNext()){
            double[] pos = iterator.next();
            glVertex3d(pos[0] * SCALE, pos[1] * SCALE, pos[2] * SCALE);
        }

        glEnd();
    }

    public void renderVectors(Moon moon){
        glColor3f(0.0f, 0.8f, 0.2f);
        double velocityScale = 0.005;
        double vectorEndX = ( this.getX() * SCALE + this.getVX() * velocityScale );
        double vectorEndY = ( this.getY() * SCALE + this.getVY() * velocityScale );
        double vectorEndZ = ( this.getZ() * SCALE + this.getVZ() * velocityScale );

        glBegin(GL_LINES); // rendering the velocity vector
        glVertex3d(this.getX() * SCALE, this.getY() * SCALE, this.getZ() * SCALE);
        glVertex3d(vectorEndX, vectorEndY, vectorEndZ);
        glEnd();

        Queue<double[]> positionHistory = moon.getPositionHistory();
        double[] oldestPosition = positionHistory.peek();

        glBegin(GL_LINES); // line between rocket and the moon
        glVertex3d(this.getX() * SCALE, this.getY() * SCALE, this.getZ() * SCALE);
        glVertex3d(oldestPosition[0] * SCALE, oldestPosition[1] * SCALE, oldestPosition[2] * SCALE);
        glEnd();
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

    public double getVX(){
        return velocityHistory.peek()[0];
    }

    public double getVY(){
        return velocityHistory.peek()[1];
    }

    public double getVZ(){
        return velocityHistory.peek()[2];
    }

    public Queue<double[]> getPositionHistory(){
        return positionHistory;
    }

    public Queue<double[]> getPositionHistory2(){
        return positionHistory2;
    }

    public Queue<double[]> getVelocityHistory2(){
        return velocityHistory2;
    }

    public void setPositionHistory(Queue<double[]> posHis2){
        this.positionHistory = posHis2;
    }

    public void setVelocityHistory(Queue<double[]> velHis2){
        this.velocityHistory = velHis2;
    }

    public void clearPositionHistory2(){
        positionHistory2.clear();
    }

    public void clearVelocityHistory2(){
        velocityHistory2.clear();
    }

    public void clearPositionHistory(){
        positionHistory.clear();
    }

    public void clearVelocityHistory(){
        velocityHistory.clear();
    }

    public void clearPosition(){
        this.position = position2;
    }

    public void clearVelocity(){
        this.velocity = velocity2;
    }

    public void clearPosition2(){
        this.position2 = position;
    }

    public void clearVelocity2(){
        this.velocity2 = velocity;
    }

    public void addPositionAndVelocity(double[] newPosition, double[] newVelocity){
        position2 = newPosition;
        velocity2 = newVelocity;
        positionHistory2.offer(newPosition.clone());
        velocityHistory2.offer(newVelocity.clone());
    }

}
