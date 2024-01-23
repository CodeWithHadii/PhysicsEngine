package com.bosonshiggs.physicsengine.helpers;

import com.bosonshiggs.physicsengine.helpers.Vector2D;
import android.util.Log;
/**
	 * Class for physical objects in the simulation.
	 */
public class PhysicsObject {
    private Vector2D position;
    private Vector2D velocity;
    private Vector2D size;
    private float mass;
    private float friction;
    
    private float angularVelocity;
    private float angularAcceleration;
    
    private boolean isPlatform;
    private boolean onPlatform;
    
    private Vector2D appliedForce = new Vector2D(0, 0);
    
    private static final float EPSILON = 0.5f;
    
    private String LOG_NAME = "PhysicsEngine";
    private boolean flagLog = true;
    
    private boolean isOscillatingHorizontally = false;
    private boolean isOscillatingVertically = false;
    private float oscillationAmplitudeX;
    private float oscillationAmplitudeY;
    private float oscillationFrequencyX;
    private float oscillationFrequencyY;
    private float initialPosX;
    private float initialPosY;
    private long startTime;
    
    //Conteiner
    private Container container;
    private Vector2D containerOffset; // Offset relativo à posição do pai

    public PhysicsObject(float x, float y, float width, float height, float mass, float friction) {
        this.position = new Vector2D(x, y);
        this.velocity = new Vector2D(0, 0);
        this.size = new Vector2D(width, height);
        this.mass = mass;
        this.friction = friction;
    }

    public void applyForce(Vector2D force) {
        this.appliedForce = force;
        Vector2D scaledForce = new Vector2D(force.x / mass, force.y / mass);
        velocity.add(scaledForce);
    }
    
    public Vector2D getAppliedForce() {
        return this.appliedForce;
    }

    public void applyFriction() {
    	velocity.multiply(1 - friction);
    }
    
    public void applyTorque(float torque) {
        angularAcceleration += torque / mass; // Assuming moment of inertia proportional to mass
    }

    public void update(float deltaTime) {
        applyFriction();
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (isOscillatingHorizontally ) {
            float newX = initialPosX + oscillationAmplitudeX * (float)Math.sin(oscillationFrequencyX * elapsedTime);
            if (flagLog) Log.d(LOG_NAME, "xPosOscillatingH: " + newX);
            setPosition(new Vector2D(newX, position.y));
        }

        if (isOscillatingVertically) {
            float newY = initialPosY + oscillationAmplitudeY * (float)Math.sin(oscillationFrequencyY * elapsedTime);
            if (flagLog) Log.d(LOG_NAME, "yPosOscillatingV: " + newY);
            setPosition(new Vector2D(position.x, newY));
        }
        
        // Verifique se a força aplicada e a velocidade são ambas efetivamente zero
        if (Math.abs(this.appliedForce.x) < EPSILON && Math.abs(this.appliedForce.y) < EPSILON &&
            Math.abs(this.velocity.x) < EPSILON && Math.abs(this.velocity.y) < EPSILON) {
            return;
        }

        Vector2D deltaVelocity = new Vector2D(velocity.x * deltaTime, velocity.y * deltaTime);
        position.add(deltaVelocity);
        angularVelocity += angularAcceleration * deltaTime;
        angularAcceleration = 0; // Resetar após a atualização
        
    }


    public boolean collidesWith(PhysicsObject other) {
        boolean collision = position.x < other.position.x + other.size.x &&
                            position.x + size.x > other.position.x &&
                            position.y < other.position.y + other.size.y &&
                            position.y + size.y > other.position.y;
        /*
        if (collision) {
            if (flagLog) Log.d(LOG_NAME, "collidesWith - Colisão detectada: Obj1 Pos=" + this.position + " Size=" + this.size +
                  " Obj2 Pos=" + other.position + " Size=" + other.size);
        }
        */

        return collision;
    }

    
 // Método para determinar o lado da colisão
    public String getCollisionSide(PhysicsObject other) {
        float dxCenter = (other.position.x + other.size.x / 2) - (this.position.x + this.size.x / 2);
        float dyCenter = (other.position.y + other.size.y / 2) - (this.position.y + this.size.y / 2);

        float width = (this.size.x + other.size.x) / 2;
        float height = (this.size.y + other.size.y) / 2;

        float crossWidth = width * dyCenter;
        float crossHeight = height * dxCenter;

        if(Math.abs(dxCenter) <= width && Math.abs(dyCenter) <= height) {
            if(crossWidth > crossHeight) {
                return (crossWidth > (-crossHeight)) ? "bottom" : "left";
            } else {
                return (crossWidth > -(crossHeight)) ? "right" : "top";
            }
        }
        return "none";
    }

 // New methods for getting and setting properties
    public Vector2D getPosition() {
        return position;
    }

    public void setPosition(Vector2D position) {
        this.position = position;
        
        if (container != null) {
            container.onParentUpdated(); // Atualizar a posição dos objetos filhos
        }
    }

    public Vector2D getSize() {
        return size;
    }

    public void setSize(Vector2D size) {
        this.size = size;
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }
    
    public Vector2D getVelocity() {
        return velocity;
    }
    
    public float getSpeed() {
        // Retorna a magnitude do vetor de velocidade
        return this.velocity.magnitude();
    }

    public float getAngularVelocity() {
        return angularVelocity;
    }
    
    // Método para definir a velocidade angular
    public void setAngularVelocity(float angularVelocity) {
        this.angularVelocity = angularVelocity;
    }
    
 // Método para definir a velocidade
    public void setVelocity(Vector2D velocity) {
        this.velocity = velocity;
    }
    
    public void setAsPlatform(boolean isPlatform) {
        this.isPlatform = isPlatform;
    }

    public boolean isPlatform() {
        return this.isPlatform;
    }
    
    public boolean isOnPlatform() {
        return onPlatform;
    }

    public void setOnPlatform(boolean onPlatform) {
        this.onPlatform = onPlatform;
    }
    
    public void startOscillatingHorizontally(float amplitude, long oscillationTime) {
        float frequency = 1.0f / oscillationTime; // Convertendo tempo em frequência
        this.isOscillatingHorizontally = true;
        this.oscillationAmplitudeX = amplitude;
        this.oscillationFrequencyX = (float)(2 * Math.PI * frequency); // Convertendo para radianos por segundo
        this.initialPosX = this.position.x;
        this.startTime = System.currentTimeMillis();
    }

    public void startOscillatingVertically(float amplitude, long oscillationTime) {
        float frequency = 1.0f / oscillationTime; // Convertendo tempo em frequência
        this.isOscillatingVertically = true;
        this.oscillationAmplitudeY = amplitude;
        this.oscillationFrequencyY = (float)(2 * Math.PI * frequency); // Convertendo para radianos por segundo
        this.initialPosY = this.position.y;
        this.startTime = System.currentTimeMillis();
    }
    
    //Conteiner setter and getter
    // Método para definir o container
    public void setContainer(Container container) {
        this.container = container;
        if (container != null) {
            this.containerOffset = new Vector2D(this.position.x - container.getParent().getPosition().x,
                                                this.position.y - container.getParent().getPosition().y);
        }
    }

    public Vector2D getContainerOffset() {
        return containerOffset;
    }
    
}