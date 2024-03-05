package com.bosonshiggs.physicsengine.helpers;

import java.util.ArrayList;
import java.util.List;

public class QuadTree {
    private final int MAX_OBJECTS = 10;
    private final int MAX_LEVELS = 5;

    private int level;
    private List<PhysicsObject> objects;
    private Rect bounds;
    private QuadTree[] nodes;

    public QuadTree(int level, Rect bounds) {
        this.level = level;
        this.bounds = bounds;
        this.objects = new ArrayList<PhysicsObject>();
        this.nodes = new QuadTree[4];
    }

    // Limpa a quad-tree
    public void clear() {
        objects.clear();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                nodes[i].clear();
                nodes[i] = null;
            }
        }
    }

    // Divide o nó em 4 subnós
    private void split() {
        int subWidth = (int)(bounds.width / 2);
        int subHeight = (int)(bounds.height / 2);
        int x = (int)bounds.x;
        int y = (int)bounds.y;

        nodes[0] = new QuadTree(level + 1, new Rect(x + subWidth, y, subWidth, subHeight));
        nodes[1] = new QuadTree(level + 1, new Rect(x, y, subWidth, subHeight));
        nodes[2] = new QuadTree(level + 1, new Rect(x, y + subHeight, subWidth, subHeight));
        nodes[3] = new QuadTree(level + 1, new Rect(x + subWidth, y + subHeight, subWidth, subHeight));
    }

    // Determina o(s) quadrante(s) em que o objeto se encaixa
    private int getIndex(PhysicsObject object) {
        int index = -1;
        double verticalMidpoint = bounds.x + (bounds.width / 2);
        double horizontalMidpoint = bounds.y + (bounds.height / 2);

        // Objeto cabe inteiramente na parte superior
        boolean topQuadrant = (object.getPosition().y < horizontalMidpoint && object.getPosition().y + object.getSize().y < horizontalMidpoint);
        // Objeto cabe inteiramente na parte inferior
        boolean bottomQuadrant = (object.getPosition().y > horizontalMidpoint);

        // Objeto cabe inteiramente no lado esquerdo
        if (object.getPosition().x < verticalMidpoint && object.getPosition().x + object.getSize().x < verticalMidpoint) {
            if (topQuadrant) {
                index = 1;
            } else if (bottomQuadrant) {
                index = 2;
            }
        }
        // Objeto cabe inteiramente no lado direito
        else if (object.getPosition().x > verticalMidpoint) {
            if (topQuadrant) {
                index = 0;
            } else if (bottomQuadrant) {
                index = 3;
            }
        }

        return index;
    }

    // Insere o objeto na quad-tree. Se o nó exceder a capacidade, ele será dividido e todos os objetos serão redistribuídos.
    public void insert(PhysicsObject object) {
        if (nodes[0] != null) {
            int index = getIndex(object);

            if (index != -1) {
                nodes[index].insert(object);

                return;
            }
        }

        objects.add(object);

        if (objects.size() > MAX_OBJECTS && level < MAX_LEVELS) {
            if (nodes[0] == null) { 
                split(); 
            }
        
            int i = 0;
            while (i < objects.size()) {
                int index = getIndex(objects.get(i));
                if (index != -1) {
                    nodes[index].insert(objects.remove(i));
                } else {
                    i++;
                }
            }
        }
    }

    // Retorna todos os objetos que podem colidir com o objeto dado
    public List<PhysicsObject> retrieve(List<PhysicsObject> returnObjects, PhysicsObject object) {
        int index = getIndex(object);
        if (index != -1 && nodes[0] != null) {
            nodes[index].retrieve(returnObjects, object);
        }

        returnObjects.addAll(objects);

        return returnObjects;
    }

    public static class Rect {
        public float x, y, width, height;

        public Rect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
