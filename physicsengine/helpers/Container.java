package com.bosonshiggs.physicsengine.helpers;

import com.bosonshiggs.physicsengine.helpers.PhysicsObject;

import java.util.ArrayList;
import java.util.List;

public class Container {
    private PhysicsObject parent;
    private List<PhysicsObject> children;

    public Container(PhysicsObject parent) {
        this.parent = parent;
        this.children = new ArrayList<>();
    }

    public void addChild(PhysicsObject child) {
        children.add(child);
        child.setContainer(this);
    }

    public void updateChildrenPositions() {
        for (PhysicsObject child : children) {
            Vector2D offset = child.getContainerOffset();
            child.setPosition(new Vector2D(parent.getPosition().x + offset.x, parent.getPosition().y + offset.y));
        }
    }

    public void removeChild(PhysicsObject child) {
        children.remove(child);
        child.setContainer(null);
    }

    public PhysicsObject getParent() {
        return parent;
    }

    // Call this method when the parent is moved or updated
    public void onParentUpdated() {
        updateChildrenPositions();
    }
}
