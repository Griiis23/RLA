package com.upjv.rla.utils;

public class PIDController {
    private double Kp, Ki, Kd;
    private double error, prevError, integral = 0;
    private double desiredSpeed;
    private double maxOutput = 100; // max control output
    private double minOutput = -100; // min control output
    private double maxIntegral = 100; // max integral term
    private double minIntegral = -100; // min integral term
    private long prevTime = System.currentTimeMillis();

    public PIDController(double Kp, double Ki, double Kd, double desiredSpeed) {
        this.Kp = Kp;
        this.Ki = Ki;
        this.Kd = Kd;
        this.desiredSpeed = desiredSpeed;
    }

    public void setDesiredSpeed(double desiredSpeed) {
        this.desiredSpeed = desiredSpeed;
    }

    public double getOutput(double currentSpeed) {
        error = desiredSpeed - currentSpeed;

        long currentTime = System.currentTimeMillis();
        double deltaTime = currentTime - prevTime / 1000; // convert to seconds

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