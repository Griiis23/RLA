package com.upjv.rla.util;

import android.util.Log;

public class PIDController {
    private double Kp = 10, Ki = 10, Kd = 1;
    private double error, prevError, integral = 0;
    private double desiredSpeed = 0;
    private double maxOutput = 100; // max control output
    private double minOutput = -100; // min control output
    private double maxIntegral = 100; // max integral term
    private double minIntegral = -100; // min integral term
    private long prevTime = System.currentTimeMillis();


    public void setDesiredSpeed(double desiredSpeed) {
        this.desiredSpeed = desiredSpeed;
    }

    public double getOutput(double currentSpeed) {
        error = desiredSpeed - currentSpeed;

        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - prevTime) / 1000.;

        integral += error * deltaTime;
        if (integral > maxIntegral) {
            integral = maxIntegral;
        } else if (integral < minIntegral) {
            integral = minIntegral;
        }

        double derivative = (error - prevError) / deltaTime;

        double output = (Kp * error) + (Ki * integral) + (Kd * derivative);

        if (output > maxOutput) {
            output = maxOutput;
        } else if (output < minOutput) {
            output = minOutput;
        }

        prevError = error;
        prevTime = currentTime;

        return output;
    }

    public void reset() {
        this.error = 0;
        this.prevError = 0;
        this.prevTime = System.currentTimeMillis();
        this.integral = 0;
    }
}