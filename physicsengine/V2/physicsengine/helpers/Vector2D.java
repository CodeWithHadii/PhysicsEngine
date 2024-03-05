package com.bosonshiggs.physicsengine.helpers;

public class Vector2D {
    public float x, y;

    public Vector2D(float x, float y) {
        this.x = x;
        this.y = y;
    }
    
    public void add(Vector2D other) {
        this.x += other.x;
        this.y += other.y;
    }
    
    public Vector2D add2D(Vector2D other) {
        return new Vector2D(this.x + other.x, this.y + other.y);
    }

    public Vector2D subtract2D(Vector2D other) {
        return new Vector2D(this.x - other.x, this.y - other.y);
    }

    public Vector2D multiply(float scalar) {
        return new Vector2D(this.x * scalar, this.y * scalar);
    }

    public Vector2D divide(float scalar) {
        return new Vector2D(this.x / scalar, this.y / scalar);
    }

    public static float distance(Vector2D v1, Vector2D v2) {
        float dx = v1.x - v2.x;
        float dy = v1.y - v2.y;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    public float magnitude() {
        return (float)Math.sqrt(x * x + y * y);
    }

    public void normalize() {
        float mag = magnitude();
        if (mag > 0) {
            x /= mag;
            y /= mag;
        }
    }

    public float dot(Vector2D other) {
        return this.x * other.x + this.y * other.y;
    }  
}