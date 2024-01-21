package com.bosonshiggs.physicsengine.helpers;

import com.bosonshiggs.physicsengine.helpers.Vector2D;
import com.bosonshiggs.physicsengine.helpers.PhysicsObject;

public class Camera {
    private Vector2D position;
    private float zoom;

    // Construtor
    public Camera(float x, float y, float zoom) {
        this.position = new Vector2D(x, y);
        this.zoom = zoom;
    }

 // Atualiza a posição da câmera para seguir um objeto
    public void update(PhysicsObject target) {
        if (target != null) {
            Vector2D targetPosition = target.getPosition();

            // Simplesmente define a posição da câmera para ser a mesma do objeto
            // Ajuste conforme necessário para alcançar o efeito desejado
            this.position.x = targetPosition.x;
            this.position.y = targetPosition.y;
        }
    }

    // Métodos setters e getters
    public void setPosition(float x, float y) {
        this.position.x = x;
        this.position.y = y;
    }

    public void setZoom(float zoom) {
        if (zoom > 0) {
            this.zoom = zoom;
        }
    }

    public Vector2D getPosition() {
        return position;
    }

    public float getZoom() {
        return zoom;
    }
}
