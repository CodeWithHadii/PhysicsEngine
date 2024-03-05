package com.bosonshiggs.physicsengine.helpers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import com.bosonshiggs.physicsengine.helpers.PhysicsObject;
import com.bosonshiggs.physicsengine.helpers.Vector2D;

public class Sprite {
    private Bitmap image; // A imagem da sprite
    private PhysicsObject physicsObject; // O objeto de física associado
    private float scale = 1.0f; // Escala padrão é 1 (sem escala)
    private float rotation = 0.0f; // Rotação em graus
    private boolean isVisible = true; // Visibilidade padrão é verdadeira
    private OriginPoint originPoint = OriginPoint.CENTER;
    private Vector2D customOrigin = new Vector2D(0, 0);
    // Limites da caixa de colisão ajustados
    private RectF bounds = new RectF();

    /**
     * Construtor da classe Sprite.
     * 
     * @param image A imagem da sprite.
     * @param physicsObject O objeto de física associado à sprite.
     */
    public Sprite(Bitmap image, PhysicsObject physicsObject) {
        this.image = image;
        this.physicsObject = physicsObject;
    }

    /**
     * Desenha a sprite no canvas, usando a posição do objeto de física associado.
     * 
     * @param canvas O canvas no qual a sprite será desenhada.
     */
    
    /**
     * Desenha a sprite no canvas, usando a posição do objeto de física associado.
     * Este método agora também leva em conta a escala, rotação e visibilidade.
     * 
     * @param canvas O canvas no qual a sprite será desenhada.
     */

    public void draw(Canvas canvas) {
        if (!isVisible) return; // Não desenha se a sprite estiver invisível

        int width = image.getWidth();
        int height = image.getHeight();
        float pivotX = 0;
        float pivotY = 0;

        switch (originPoint) {
            case TOP_LEFT:
                pivotX = 0;
                pivotY = 0;
                break;
            case TOP_RIGHT:
                pivotX = width;
                pivotY = 0;
                break;
            case BOTTOM_RIGHT:
                pivotX = width;
                pivotY = height;
                break;
            case BOTTOM_LEFT:
                pivotX = 0;
                pivotY = height;
                break;
            case CENTER:
                pivotX = width * 0.5f;
                pivotY = height * 0.5f;
                break;
            case CUSTOM:
                pivotX = customOrigin.x;
                pivotY = customOrigin.y;
                break;
        }

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postTranslate(-pivotX, -pivotY);
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        Vector2D position = physicsObject.getPosition();
        matrix.postTranslate(position.x + pivotX, position.y + pivotY);

        canvas.drawBitmap(image, matrix, null);
    }
    
    public Vector2D calculateOriginOffset() {
        float offsetX = 0;
        float offsetY = 0;
        switch (originPoint) {
            case TOP_LEFT:
                offsetX = 0;
                offsetY = 0;
                break;
            case TOP_RIGHT:
                offsetX = -image.getWidth();
                offsetY = 0;
                break;
            case BOTTOM_RIGHT:
                offsetX = -image.getWidth();
                offsetY = -image.getHeight();
                break;
            case BOTTOM_LEFT:
                offsetX = 0;
                offsetY = -image.getHeight();
                break;
            case CENTER:
                offsetX = -image.getWidth() / 2f;
                offsetY = -image.getHeight() / 2f;
                break;
            case CUSTOM:
                offsetX = -customOrigin.x;
                offsetY = -customOrigin.y;
                break;
        }
        return new Vector2D(offsetX, offsetY);
    }
    
    /**
     * Obtém a coordenada X do ponto de origem da sprite.
     * 
     * @return A coordenada X do ponto de origem.
     */
    public float getOriginX() {
    	Vector2D originOffset = calculateOriginOffset();
    	return originOffset.x;
    }
    
    /**
     * Obtém a coordenada Y do ponto de origem da sprite.
     * 
     * @return A coordenada Y do ponto de origem.
     */
    public float getOriginY() {
    	Vector2D originOffset = calculateOriginOffset();
    	return originOffset.y;
    }
    
    public void setOriginPoint(OriginPoint originPoint, Vector2D customOrigin) {
        this.originPoint = originPoint;
        if (customOrigin != null) {
            this.customOrigin = customOrigin;
        }
    }
    
    /**
     * Obtém o ponto de origem atual da sprite.
     * 
     * @return O ponto de origem da sprite.
     */
    public OriginPoint getOriginPoint() {
        return this.originPoint;
    }
    
    /**
     * Atualiza a posição do objeto de física associado à sprite.
     * 
     * @param x A nova posição X para o objeto de física.
     * @param y A nova posição Y para o objeto de física.
     */
    public void updatePosition(float x, float y) {
        if (this.physicsObject != null) {
            this.physicsObject.setPosition(new Vector2D(x, y));
            updateBounds(); // Atualiza os limites após a mudança de posição
        }
    }


    /**
     * Atualiza a imagem da sprite.
     * 
     * @param image A nova imagem para a sprite.
     */
    public void setImage(Bitmap image) {
        this.image = image;
    }
    
    /**
     * Obtém a imagem atual da sprite.
     * 
     * @return A imagem (`Bitmap`) da sprite.
     */
    public Bitmap getImage() {
        return this.image;
    }


    /**
     * Obtém o objeto de física associado à sprite.
     * 
     * @return O objeto de física associado.
     */
    public PhysicsObject getPhysicsObject() {
        return physicsObject;
    }

    /**
     * Define um novo objeto de física para a sprite.
     * 
     * @param physicsObject O novo objeto de física a ser associado.
     */
    public void setPhysicsObject(PhysicsObject physicsObject) {
        this.physicsObject = physicsObject;
    }
    
    /**
     * Define a escala da sprite.
     * 
     * @param scale A nova escala para a sprite.
     */
    public void setScale(float scale) {
        this.scale = scale;
        updateBounds(); // Atualiza os limites após a mudança de escala
    }

    /**
     * Obtém a escala atual da sprite.
     * 
     * @return A escala da sprite.
     */
    public float getScale() {
        return this.scale;
    }

    /**
     * Define a rotação da sprite.
     * 
     * @param rotation A nova rotação para a sprite, em graus.
     */
    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    /**
     * Obtém a rotação atual da sprite.
     * 
     * @return A rotação da sprite, em graus.
     */
    public float getRotation() {
        return this.rotation;
    }

    /**
     * Define a visibilidade da sprite.
     * 
     * @param isVisible `true` para tornar a sprite visível; `false` para invisível.
     */
    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

    /**
     * Verifica se a sprite está visível.
     * 
     * @return `true` se a sprite estiver visível, `false` caso contrário.
     */
    public boolean isVisible() {
        return this.isVisible;
    }
    
 // Atualiza os limites da caixa de colisão sempre que a posição ou o tamanho da sprite mudar
    public void updateBounds() {
        Vector2D position = physicsObject.getPosition();
        Vector2D size = physicsObject.getSize();
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        bounds.set(position.x, position.y, position.x + width, position.y + height);
    }

    // Retorna os limites da caixa de colisão da sprite
    public RectF getBounds() {
        return bounds;
    }
    
}
