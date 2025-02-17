package com.example;

import org.joml.Matrix4d;
import org.lwjgl.opengl.GL11;

public class OpenGLUtils {

    public static void perspectiveGL(float fovY, float aspect, float zNear, float zFar){
        Matrix4d projection = new Matrix4d().perspective(Math.toRadians(fovY), aspect, zNear, zFar);
        GL11.glLoadMatrixd(projection.get(new double[16]));
    }

    public static void lookAtGL(double eyeX, double eyeY, double eyeZ,
                                double centerX, double centerY, double centerZ,
                                double upX, double upY, double upZ) {
        Matrix4d view = new Matrix4d().lookAt(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);
        GL11.glLoadMatrixd(view.get(new double[16]));
    }
}

