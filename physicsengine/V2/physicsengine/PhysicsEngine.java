package com.bosonshiggs.physicsengine;

import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.OptionList;
import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.runtime.util.YailList;

import com.bosonshiggs.physicsengine.helpers.Vector2D;
import com.bosonshiggs.physicsengine.helpers.PhysicsObject;
import com.bosonshiggs.physicsengine.helpers.Camera;
import com.bosonshiggs.physicsengine.helpers.Container;
import com.bosonshiggs.physicsengine.helpers.FollowInfo;
import com.bosonshiggs.physicsengine.helpers.Sprite;
import com.bosonshiggs.physicsengine.helpers.OriginPoint;
import com.bosonshiggs.physicsengine.helpers.QuadTree;

import java.util.HashMap;
import java.util.Map;

import java.util.List;
import java.util.ArrayList;

import java.util.Comparator;
import java.util.Collections;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;

import java.util.Set;
import java.util.HashSet;

import android.os.Build;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.RectF;

import android.app.Activity;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;

import android.util.Log;

import android.util.LruCache;

@DesignerComponent(
	    version = 2,
	    description = "The PhysicsEngine extension brings simple yet effective physics simulation.",
	    category = ComponentCategory.EXTENSION,
	    nonVisible = true,
	    iconName = "aiwebres/icon.png"
	)
@SimpleObject(external = true)
public class PhysicsEngine extends AndroidNonvisibleComponent {
    private Map<Integer, PhysicsObject> objects = new HashMap<>();
    private HashMap<Integer, String> objectToLayerMap = new HashMap<>();
    private HashMap<String, Runnable> animationTasks = new HashMap<>();
    private Map<Integer, Container> containers = new HashMap<>();
    private Map<Integer, Sprite> sprites = new HashMap<>();
    
    // Mapa para armazenar os objetos que estão seguindo outros objetos
    private Map<Integer, FollowInfo> followingObjects = new HashMap<>();

    private Vector2D gravity = new Vector2D(0, 9.8f);
    
    private String LOG_NAME = "PhysicsEngine";
    private boolean flagLog = false;
    
    // Pool de threads para melhorar a eficiência
    private ExecutorService collisionExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private ExecutorService touchEventExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService touchEventExecutorMs = Executors.newSingleThreadExecutor();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    
    private ScheduledExecutorService scheduler;
    
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    
    // Handler para atualizações periódicas
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateTask;
    
    private boolean collisionDetected = false;
    
    private static final float EPSILON = 0.5f;
    
    private ComponentContainer container;
    private Canvas canvasComponent;
    private Context context;
    
    private String activeLayerName;
    
    //Animations
    private Handler animationHandler = new Handler(Looper.getMainLooper());
    private Runnable animationTask;
    private int currentFrameIndex = 0;
    private ArrayList<String> framePaths;
    private int frameDurationMs;
    private String canvasObjectId;
    private String currentAnimationId;
    
    private HashMap<String, Layer> layerMap = new HashMap<>();
    private int canvasWidth = 800; // Largura padrão
    private int canvasHeight = 600; // Altura padrão
    
    private boolean showCollisionBoxes = false;
    
    private Camera camera;
    
    private boolean isParallaxEnabled = true;
    
 // Variáveis globais para controlar a exibição das coordenadas e do FPS
    private boolean showTouchInfo = false;
    private float touchX = 0;
    private float touchY = 0;
    private long lastTouchTime = System.currentTimeMillis();
    private float fps = 0;
    
    
    //Dev mode
    private RectF lastTouchBounds = new RectF();
    
    private Vector2D lastTouchPosition = new Vector2D(0, 0);
    private boolean showTouchesAndBounds = false;
    
    private Bitmap finalBitmap = null;
    private Paint paint;
    private final Object bitmapLock = new Object();
    
 // Inicializa o cache de bitmaps
    private BitmapCache bitmapCache = new BitmapCache();
    
    public PhysicsEngine(ComponentContainer container) {
        super(container.$form());
        this.container = container;
        this.context = container.$context();
        
        
     // Inicialize a câmera com valores padrão
        this.camera = new Camera(0, 0, 1.0f); // Posição (0,0) com zoom padrão 1
        
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
     // Inicialização do PhysicsEngine...
        paint = new Paint();
    }
    
    /*
     * PROPERTY
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Sets the Canvas component used for drawing.")
    public void SetCanvas(Canvas canvas) {
        this.canvasComponent = canvas;
        SetCanvasMonitoring(canvas);
    }
    
    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public int GetCanvasWidth() {
        return this.canvasWidth;
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public int GetCanvasHeight() {
        return this.canvasHeight;
    }
    
    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public void SetCanvasWidth(int newWidth) {
        this.canvasWidth = newWidth;
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public void SetCanvasHeight(int newHeight) {
        this.canvasHeight = newHeight;
    }
    /*
     * END PROPERTY
     */

    public void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            try {
                // Espera o termino das tarefas ou interrompe após um timeout
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Executor não finalizou dentro do tempo.");
                }
            } catch (InterruptedException ie) {
                // (Re)Cancela se a thread atual também for interrompida
                scheduler.shutdownNow();
                // Preserva o status de interrupção
                Thread.currentThread().interrupt();
            }
        }
    }

    @SimpleFunction(description = "Stops periodic updates for the physics simulation.")
    public void StopUpdates() {
        if (updateTask != null) {
            // Remove callbacks e mensagens para a tarefa de atualização
            updateHandler.removeCallbacks(updateTask);
        }
    }
  
    @SimpleFunction(description = "Adds a physical object to the simulation with specified properties.")
    public void AddObject(int id, float x, float y, float width, float height, float mass, float friction) {
        objects.put(id, new PhysicsObject(x, y, width, height, mass, friction));
    }
    
    @SimpleFunction(description = "Applies a temporary force to an object identified by its ID.")
    public void ApplyForce(int id, float forceX, float forceY, final int durationMs) {
        final PhysicsObject obj = objects.get(id);
        if (obj != null) {
            // Aplica a força
            obj.applyForce(new Vector2D(forceX, forceY));

            // Cria um manipulador para redefinir a força após a duração especificada
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Redefine a força aplicada para zero
                    obj.applyForce(new Vector2D(0, 0));
                }
            }, durationMs);

            // Atualiza o estado onPlatform do objeto
            updateOnPlatformState(obj);
        }
    }

       
    @SimpleFunction(description = "Removes an object from the simulation based on its ID.")
    public void RemoveObject(int id) {
        objects.remove(id);
    }
    
    @SimpleFunction(description = "Clears all objects from the physics simulation.")
    public void ClearObjects() {
        objects.clear();
    }

    @SimpleFunction(description = "Checks if two objects are colliding.")
    public boolean AreObjectsColliding(int id1, int id2) {
        PhysicsObject obj1 = objects.get(id1);
        PhysicsObject obj2 = objects.get(id2);
        if (obj1 != null && obj2 != null) {
            return obj1.collidesWith(obj2);
        }
        return false;
    }

    @SimpleFunction(description = "Updates the properties of an existing object.")
    public void SetObjectProperties(int id, float x, float y, float width, float height, float mass, float friction) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setPosition(new Vector2D(x, y));
            obj.setSize(new Vector2D(width, height));
            obj.setMass(mass);
            obj.setFriction(friction);
            
         // Atualiza o estado onPlatform do objeto
            updateOnPlatformState(obj);
        }
    }

    @SimpleFunction(description = "Returns the position of an object as a list [x, y].")
    public YailList GetObjectPosition(int id) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            Vector2D pos = obj.getPosition();
            return YailList.makeList(new Object[]{pos.x, pos.y});
        }
        return YailList.makeList(new Object[]{0f, 0f});
    }
    
    @SimpleFunction(description = "Sets the global gravity affecting all objects.")
    public void SetGravity(float x, float y) {
    	this.gravity = new Vector2D(x, y);
    }

    @SimpleFunction(description = "Returns the velocity of an object as a list [vx, vy].")
    public YailList GetObjectVelocity(int id) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            Vector2D vel = obj.getVelocity();
            return YailList.makeList(new Object[]{vel.x, vel.y});
        }
        return YailList.makeList(new Object[]{0f, 0f});
    }
    
    /*
     * BEGIN: Treat Physics individually for each object
     */
    //@SimpleFunction(description = "Updates the physics simulation, progressing time by the specified delta.")
 // Método auxiliar para determinar se dois objetos estão próximos o suficiente para considerar colisão
    @SimpleFunction(description = "Starts periodic updates for the physics simulation.")
    public void StartUpdates(final int updateTimeMs) {
        // Certifica-se de que o scheduler anterior seja fechado corretamente antes de criar um novo
        shutdownScheduler();

        // Cria um novo ScheduledExecutorService
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Agenda a tarefa de atualização
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                // Sua lógica de atualização aqui
                update(updateTimeMs / 1000.0f);
            }
        }, 0, updateTimeMs, TimeUnit.MILLISECONDS);
    }
    
    private boolean areObjectsClose(PhysicsObject obj1, PhysicsObject obj2) {
        float maxDistance = 100.0f; // Define o máximo de distância para considerar colisões
        float dx = obj1.getPosition().x - obj2.getPosition().x;
        float dy = obj1.getPosition().y - obj2.getPosition().y;
        return (dx * dx + dy * dy) <= (maxDistance * maxDistance);
    }
    
    public synchronized void update(final float deltaTimeMs) {
        final float deltaTime = deltaTimeMs / 1000.0f;
        try {
           if(flagLog) Log.d(LOG_NAME, "Iniciando a atualização do ciclo de física");

            QuadTree.Rect bounds = new QuadTree.Rect(0, 0, canvasWidth, canvasHeight);
            QuadTree quadTree = new QuadTree(0, bounds);

            synchronized (this.objects) {
                for (PhysicsObject obj : new ArrayList<>(objects.values())) {
                    quadTree.insert(obj);
                }

                for (PhysicsObject obj : new ArrayList<>(objects.values())) {
                    updateObject(obj, deltaTime);
                }
            }

            Set<String> checkedPairs = new HashSet<>();
            synchronized (this.objects) {
            	for (Map.Entry<Integer, PhysicsObject> entry : objects.entrySet()) {
                    PhysicsObject obj1 = entry.getValue();
                    List<PhysicsObject> candidates = quadTree.retrieve(new ArrayList<PhysicsObject>(), obj1);

                    for (PhysicsObject obj2 : candidates) {
                        if (obj1 == obj2) continue;
                        
                        String pairKey = Math.min(entry.getKey(), findObjectId(obj2)) + "-" + Math.max(entry.getKey(), findObjectId(obj2));
                        if (!checkedPairs.add(pairKey)) continue;

                        if (obj1.collidesWith(obj2)) {
                            String collisionSide = determineCollisionSide(obj1, obj2);
                            handleCollision(obj1, obj2, collisionSide);
                        }
                    }
                }
            }

            if (isParallaxEnabled) {
                synchronized (this.layerMap) {
                    for (Layer layer : new ArrayList<>(layerMap.values())) {
                        if (layer.parallaxIntensity > 0.0f) {
                            updateLayerParallax(layer);
                        }
                    }
                }
            }

            synchronized (this.sprites) {
                for (Map.Entry<Integer, Sprite> entry : new HashMap<>(sprites).entrySet()) {
                    Sprite sprite = entry.getValue();
                    PhysicsObject obj = objects.get(entry.getKey());
                    if (obj != null) {
                        sprite.updatePosition(obj.getPosition().x, obj.getPosition().y);
                    }
                }
            }
            
            synchronized (this.objects) {
	            for (PhysicsObject obj : objects.values()) {
	                boolean onPlatform = false;
	                for (PhysicsObject platform : objects.values()) {
	                    if (platform.isPlatform() && obj.getPosition().y + obj.getSize().y <= platform.getPosition().y && Math.abs(obj.getPosition().x - platform.getPosition().x) < platform.getSize().x) {
	                        onPlatform = true;
	                        break;
	                    }
	                }
	                obj.setOnPlatform(onPlatform);
	            }
            }

            updateFollowing(deltaTime);

            if(flagLog) Log.d(LOG_NAME, "Finalizando a atualização do ciclo de física");
        } catch (Exception e) {
            Log.e(LOG_NAME, "Erro durante a atualização do ciclo de física", e);
        } finally {
            OnUpdate();
        }
    }
    
    private String determineCollisionSide(PhysicsObject obj1, PhysicsObject obj2) {
        // Este é um exemplo simples. Você precisará ajustar a lógica com base na sua implementação específica
        if (obj1.getPosition().x < obj2.getPosition().x) {
            return "left";
        } else if (obj1.getPosition().x > obj2.getPosition().x) {
            return "right";
        } else if (obj1.getPosition().y < obj2.getPosition().y) {
            return "top";
        } else {
            return "bottom";
        }
    }

    private synchronized void updateObject(PhysicsObject obj, float deltaTime) {
        // Atualiza o estado onPlatform do objeto
        updateOnPlatformState(obj);

        // Aplica a gravidade se o objeto não estiver sobre uma plataforma
        if (!obj.isOnPlatform()) {
            obj.applyForce(this.gravity);
        } else {
            obj.applyForce(new Vector2D(0f, 0f));
        }

        // Atualiza o objeto
        obj.update(deltaTime);

        // Verifica colisões com outros objetos
        checkCollisionsForObject(obj);

        // Dispara o evento de posição alterada
        final int objectId = findObjectId(obj);
        final Vector2D newPosition = obj.getPosition();
        OnPositionChanged(objectId, newPosition.x, newPosition.y);
        
        // Se as caixas de colisão estão ativadas, redesenha o canvas com elas
        if (showCollisionBoxes) {
        	RedrawCanvas(-1, true);
        } else {
        	 RedrawCanvas(-1, showCollisionBoxes);
        }
        
        if (this.isParallaxEnabled) {
            UpdateParallaxEffectRelativeToObject(objectId);
        }
    }
    
    private void handleBilliardBallCollision(PhysicsObject obj1, PhysicsObject obj2) {
        // Calcula o vetor normal da colisão
        Vector2D collisionVector = obj1.getPosition().subtract2D(obj2.getPosition());
        collisionVector.normalize();

        // Calcula a diferença de velocidade
        Vector2D velocityDifference = obj1.getVelocity().subtract2D(obj2.getVelocity());

        // Calcula a velocidade relativa ao longo do vetor normal da colisão
        float velocityAlongNormal = velocityDifference.dot(collisionVector);

        // Não prossegue se os objetos estão se afastando
        if (velocityAlongNormal > 0) {
            return;
        }

        // Calcula o coeficiente de restituição (e = 1 para uma colisão perfeitamente elástica)
        float restitution = 1.0f;

        // Calcula o escalar de impulso que deve ser aplicado aos objetos
        float j = -(1 + restitution) * velocityAlongNormal;
        j /= (1 / obj1.getMass() + 1 / obj2.getMass());

        // Aplica o impulso aos objetos
        Vector2D impulse = collisionVector.multiply(j);
        obj1.setVelocity(obj1.getVelocity().add2D(impulse.divide(obj1.getMass())));
        obj2.setVelocity(obj2.getVelocity().subtract2D(impulse.divide(obj2.getMass())));
    }

    private void checkCollisionsForObject(PhysicsObject obj) {
        int objId = findObjectId(obj);

        for (Map.Entry<Integer, PhysicsObject> entry : objects.entrySet()) {
            int otherId = entry.getKey();
            if (objId != otherId) {
                PhysicsObject other = entry.getValue();
                if (obj.collidesWith(other)) {
                	checkCollisions(obj, other);
                }
            }
        }
    }
    /*
     * END: Treat Physics individually for each object
     */
    
 // Método adicional para atualizar a velocidade angular
    @SimpleFunction(description = "Updates the angular velocity of an object.")
    public void UpdateAngularVelocity(int id, float angularVelocity) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setAngularVelocity(angularVelocity);
            
         // Atualiza o estado onPlatform do objeto
            updateOnPlatformState(obj);
        }
    }

    // Método para aplicar torque a um objeto
    @SimpleFunction(description = "Applies torque to an object.")
    public void ApplyTorque(int id, float torque) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.applyTorque(torque);
            
         // Atualiza o estado onPlatform do objeto
            updateOnPlatformState(obj);
        }
    }

    // Método para obter a velocidade angular de um objeto
    @SimpleFunction(description = "Gets the angular velocity of an object.")
    public float GetAngularVelocity(int id) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            return obj.getAngularVelocity();
        }
        return 0f;
    }
    
    @SimpleFunction(description = "Returns the mass of an object.")
    public float GetObjectMass(int id) {
        PhysicsObject obj = objects.get(id);
        return obj != null ? obj.getMass() : 0f;
    }
    
    @SimpleFunction(description = "Makes an object jump by applying an upward force for a short duration.")
    public void MakeObjectJump(final int id, final float jumpStrength, final int durationMs) {
        final PhysicsObject obj = objects.get(id);
        if (obj != null) {
            final float jumpForce = jumpStrength * obj.getMass();
            obj.applyForce(new Vector2D(0, jumpForce));

            if (flagLog) Log.d(LOG_NAME, "Salto iniciado com força: " + jumpForce);

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                	if (flagLog) Log.d(LOG_NAME, "Finalizando salto para o objeto: " + id);
                    obj.applyForce(new Vector2D(0, 0));
                }
            }, durationMs);
            
         // Atualiza o estado onPlatform do objeto
            updateOnPlatformState(obj);
        }
    }

    @SimpleFunction(description = "Sets the velocity of an object.")
    public void SetObjectVelocity(int id, float velocityX, float velocityY) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setVelocity(new Vector2D(velocityX, velocityY));
            
         // Atualiza o estado onPlatform do objeto
            updateOnPlatformState(obj);
        }
    }

    @SimpleFunction(description = "Sets the size of an object.")
    public void SetObjectSize(int id, float width, float height) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setSize(new Vector2D(width, height));
        }
    }
    
    @SimpleFunction(description = "Sets the position of an object.")
    public void SetObjectPosition(int id, float x, float y) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            // Obtém a sprite associada ao objeto de física
            Sprite sprite = sprites.get(id);
            
            if (sprite != null) {
                // Calcula a posição ajustada com base no ponto de origem da sprite
                Vector2D originOffset = sprite.calculateOriginOffset();
                
                // Ajusta as coordenadas (x, y) para que o ponto de origem especificado da sprite esteja nessas coordenadas
                float adjustedX = x + originOffset.x;
                float adjustedY = y + originOffset.y;
                
                // Atualiza a posição do objeto de física
                obj.setPosition(new Vector2D(adjustedX, adjustedY));
            } else {
                // Se não houver uma sprite associada, simplesmente atualiza a posição do objeto de física
                obj.setPosition(new Vector2D(x, y));
            }

            // Atualiza o estado de estar sobre uma plataforma, se necessário
            updateOnPlatformState(obj);
            
            // Se as caixas de colisão estão ativadas, redesenha o canvas com elas
            if (showCollisionBoxes) {
                RedrawCanvas(id, showCollisionBoxes);
            }
        }
    }
    
    @SimpleFunction(description = "Sets an object as a platform.")
    public void SetObjectAsPlatform(int id, boolean isPlatform) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setAsPlatform(isPlatform);
        }
    }
    
    @SimpleFunction(description = "Gets the force applied on an object along the X-axis.")
    public float GetObjectForceX(int id) {
        PhysicsObject obj = objects.get(id);
        return obj != null ? obj.getAppliedForce().x : 0f;
    }

    @SimpleFunction(description = "Gets the force applied on an object along the Y-axis.")
    public float GetObjectForceY(int id) {
        PhysicsObject obj = objects.get(id);
        return obj != null ? obj.getAppliedForce().y : 0f;
    }
    
    @SimpleFunction(description = "Verifica se o objeto especificado está sobre uma plataforma.")
    public boolean IsOnPlatform(int objectId) {
        PhysicsObject obj = objects.get(objectId);
        if (obj != null) {
            return obj.isOnPlatform();
        }
        return false;
    }
    
    @SimpleFunction(description = "Checks whether the specified object is stopped.")
    public boolean IsStationary(int id) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            return Math.abs(obj.getVelocity().x) < EPSILON && Math.abs(obj.getVelocity().y) < EPSILON;
        }
        return true; // Considera parado se o objeto não existir
    }

    @SimpleFunction(description = "Checks whether the specified object is skipping.")
    public boolean IsJumping(int id) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            return !obj.isOnPlatform() && Math.abs(obj.getVelocity().y) > EPSILON;
        }
        return false;
    }

    @SimpleFunction(description = "Checks whether the specified object is moving.")
    public boolean IsMoving(int id) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            return (Math.abs(obj.getVelocity().x) > EPSILON || Math.abs(obj.getVelocity().y) > EPSILON) && !IsJumping(id);
        }
        return false;
    }
    
 // Método para definir a posição X de um objeto
    @SimpleFunction(description = "Sets the X position of an object.")
    public void SetObjectPositionX(int id, float x) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setPosition(new Vector2D(x, obj.getPosition().y));
            updateOnPlatformState(obj);
        }
    }

    // Método para definir a posição Y de um objeto
    @SimpleFunction(description = "Sets the Y position of an object.")
    public void SetObjectPositionY(int id, float y) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setPosition(new Vector2D(obj.getPosition().x, y));
            updateOnPlatformState(obj);
        }
    }

    // Método para definir a velocidade X de um objeto
    @SimpleFunction(description = "Sets the X velocity of an object.")
    public void SetObjectVelocityX(int id, float velocityX) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setVelocity(new Vector2D(velocityX, obj.getVelocity().y));
        }
    }

    // Método para definir a velocidade Y de um objeto
    @SimpleFunction(description = "Sets the Y velocity of an object.")
    public void SetObjectVelocityY(int id, float velocityY) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setVelocity(new Vector2D(obj.getVelocity().x, velocityY));
        }
    }

    // Método para definir a altura de um objeto
    @SimpleFunction(description = "Sets the height of an object.")
    public void SetObjectHeight(int id, float height) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setSize(new Vector2D(obj.getSize().x, height));
        }
    }

    // Método para definir a largura de um objeto
    @SimpleFunction(description = "Sets the width of an object.")
    public void SetObjectWidth(int id, float width) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setSize(new Vector2D(width, obj.getSize().y));
        }
    }

    // Método para definir a força aplicada no eixo X de um objeto
    @SimpleFunction(description = "Sets the force applied on an object along the X-axis.")
    public void SetObjectForceX(int id, float forceX) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.applyForce(new Vector2D(forceX, obj.getAppliedForce().y));
        }
    }

    // Método para definir a força aplicada no eixo Y de um objeto
    @SimpleFunction(description = "Sets the force applied on an object along the Y-axis.")
    public void SetObjectForceY(int id, float forceY) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.applyForce(new Vector2D(obj.getAppliedForce().x, forceY));
        }
    }

    // Método para definir a gravidade global
    @SimpleFunction(description = "Sets the global gravity.")
    public void SetGlobalGravity(float x, float y) {
        this.gravity = new Vector2D(x, y);
    }
    
 // Método para definir a massa de um objeto
    @SimpleFunction(description = "Sets the mass of an object.")
    public void SetObjectMass(int id, float mass) {
        PhysicsObject obj = objects.get(id);
        if (obj != null) {
            obj.setMass(mass);
        }
    }
    
    @SimpleFunction(description = "Clean up resources and shut down the physics simulation.")
    public void ShutDown() {
        // Interrompe atualizações periódicas para evitar mais chamadas de update
        updateHandler.removeCallbacks(updateTask);

        // Limpa a coleção de objetos físicos
        objects.clear();

        // Fecha o serviço executor para liberar recursos do sistema
        if (!collisionExecutor.isShutdown()) {
            collisionExecutor.shutdown();
        }
        
        if (!touchEventExecutor.isShutdown()) {
        	touchEventExecutor.shutdown();
        }
        
        if (!touchEventExecutorMs.isShutdown()) {
        	touchEventExecutorMs.shutdown();
        }

        if (!executor.isShutdown()) {
        	executor.shutdown();
        }
        
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        
        // Reciclar todos os bitmaps de camadas
        for (Layer layer : layerMap.values()) {
            recycleBitmap(layer.bitmap);
        }

        // Tenta interromper todas as threads em execução
        try {
            if (!collisionExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                collisionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            collisionExecutor.shutdownNow();
        }

        // Log para confirmação
        if (flagLog) Log.d(LOG_NAME, "PhysicsEngine shut down and resources cleaned up.");
    }
        
    @SimpleFunction(description = "Mirror an image horizontally.")
    public synchronized String MirrorImageHorizontally(@Asset String imagePath) {
        try {
            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();

            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            Bitmap mirroredBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, 
                                                        originalBitmap.getWidth(), 
                                                        originalBitmap.getHeight(), 
                                                        matrix, false);
            
            String path = container.$form().getExternalFilesDir(null).getAbsolutePath() 
                          + "/mirrored_horizontal_" + System.currentTimeMillis() + ".png";
            
            OutputStream outputStream = new FileOutputStream(path);
            mirroredBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();

            return path;
        } catch (IOException e) {
            e.printStackTrace();
            ReportError("Erro ao espelhar imagem horizontalmente: " + e.getMessage());
            return ""; // Retornando uma string vazia em caso de erro
        }
    }

    @SimpleFunction(description = "Mirror an image vertically.")
    public synchronized String MirrorImageVertically(@Asset String imagePath) {
        try {
            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();

            Matrix matrix = new Matrix();
            matrix.preScale(1.0f, -1.0f);
            Bitmap mirroredBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, 
                                                        originalBitmap.getWidth(), 
                                                        originalBitmap.getHeight(), 
                                                        matrix, false);
            
            String path = container.$form().getExternalFilesDir(null).getAbsolutePath() 
                          + "/mirrored_vertically_" + System.currentTimeMillis() + ".png";
            
            OutputStream outputStream = new FileOutputStream(path);
            mirroredBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();

            return path;
        } catch (IOException e) {
            e.printStackTrace();
            ReportError("Erro ao espelhar imagem verticalmente: " + e.getMessage());
            return ""; // Retornando uma string vazia em caso de erro
        }
    }
    
    @SimpleFunction(description = "Start animating a sequence of images on a canvas object.")
    public synchronized void StartAnimation(YailList imagePathsList, final int frameDurationMs, final String objectId) {
        this.framePaths = convertYailListToStringList(imagePathsList);
        this.frameDurationMs = frameDurationMs;
        this.canvasObjectId = objectId;
        this.currentFrameIndex = 0;

        if (framePaths.isEmpty() || objectId == null || frameDurationMs <= 0) {
            ReportError("Invalid animation parameters.");
            return;
        }

        Runnable newAnimationTask = new Runnable() {
            @Override
            public void run() {
                if (currentFrameIndex < framePaths.size()) {
                    String imagePath = framePaths.get(currentFrameIndex);
                    OnAnimationFrameChanged(objectId, imagePath);
                    currentFrameIndex++;
                } else {
                    currentFrameIndex = 0; // Reset to the first frame for looping
                }
                animationHandler.postDelayed(this, frameDurationMs);
            }
        };

        // Para qualquer animação existente para este objectId antes de iniciar uma nova
        StopAnimation(objectId);

        // Inicia a nova animação e armazena a tarefa
        animationHandler.post(newAnimationTask);
        animationTasks.put(objectId, newAnimationTask);
    }

    private ArrayList<String> convertYailListToStringList(YailList yailList) {
        ArrayList<String> list = new ArrayList<>();
        for (Object item : yailList.toArray()) {
            list.add(item.toString());
        }
        return list;
    }
    
    @SimpleFunction(description = "Check if there is an active animation for the specified object ID.")
    public synchronized boolean IsAnimationActive(String objectId) {
        return animationTasks.containsKey(objectId);
    }
    
    @SimpleFunction(description = "Stop the animation with the specified ID.")
    public synchronized void StopAnimation(String objectId) {
        Runnable taskToStop = animationTasks.get(objectId);
        if (taskToStop != null) {
            animationHandler.removeCallbacks(taskToStop);
            animationTasks.remove(objectId); // Remove a tarefa do mapa
        }
    }
    
    /*
     * LAYERS
     */
    
    class Layer {
        Bitmap bitmap;
        int zIndex;
        float parallaxIntensity; // Nova propriedade para armazenar a intensidade do efeito de paralaxe

        Layer(Bitmap bitmap, int zIndex) {
            this.bitmap = bitmap;
            this.zIndex = zIndex;
            this.parallaxIntensity = 0.0f; // Inicializar com 0.0f por padrão
        }
    }
    
    @SimpleFunction(description = "Create a new layer.")
    public void CreateLayer(String layerName, int zIndex) {
        Bitmap layerBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        layerMap.put(layerName, new Layer(layerBitmap, zIndex));
        
        activeLayerName = layerName;
        RedrawCanvas(-1, showCollisionBoxes);
    }
    
    @SimpleFunction(description = "Set an active layer by its name.")
    public void SetActiveLayer(String layerName) {
        if (!layerMap.containsKey(layerName)) {
            ReportError("Layer not found: " + layerName);
            return;
        }
        activeLayerName = layerName;
        RedrawCanvas(-1, showCollisionBoxes);
    }

    @SimpleFunction(description = "Get the name of the active layer.")
    public String GetActiveLayer() {
        return activeLayerName;
    }
    
    @SimpleFunction(description = "Get and resize an image, returning its file path.")
    public String GetResizedImage(@Asset String imagePath, int newWidth, int newHeight) {
        try {
            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();

            // Verifica se as dimensões são válidas
            if (newWidth <= 0 || newHeight <= 0) {
                ReportError("Invalid image dimensions: Width and height must be positive.");
                return "";
            }

            // Redimensiona a imagem
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);

            // Salva a imagem redimensionada em um arquivo temporário
            String path = container.$form().getExternalFilesDir(null).getAbsolutePath() 
                          + "/resized_image_" + System.currentTimeMillis() + ".png";
            
            OutputStream outputStream = new FileOutputStream(path);
            resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();

            return path; // Retorna o caminho do arquivo da imagem redimensionada
        } catch (IOException e) {
            e.printStackTrace();
            ReportError("Error while resizing the image: " + e.getMessage());
            return ""; // Retornando uma string vazia em caso de erro
        }
    }

    
    @SimpleFunction(description = "Get a image.")
    public String GetImage(@Asset String imagePath) {
        try {
            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();
                       
            String path = container.$form().getExternalFilesDir(null).getAbsolutePath() 
                          + "/mirrored_horizontal_" + System.currentTimeMillis() + ".png";
            
            OutputStream outputStream = new FileOutputStream(path);
            originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();

            return path;
        } catch (IOException e) {
            e.printStackTrace();
            ReportError("Erro ao espelhar imagem horizontalmente: " + e.getMessage());
            return ""; // Retornando uma string vazia em caso de erro
        }
    }

    @SimpleFunction(description = "Add an image to a specific layer.")
    public void AddImageToLayer(String layerName, @Asset String imagePath) {
        try {
            if (!layerMap.containsKey(layerName)) {
                ReportError("Layer not found: " + layerName);
                return;
            }

            Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);
            if (imageBitmap == null) {
                Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
                imageBitmap = ((BitmapDrawable) drawable).getBitmap();
            }

            Layer layer = layerMap.get(layerName);
            android.graphics.Canvas canvas = new android.graphics.Canvas(layer.bitmap);
            canvas.drawBitmap(imageBitmap, 0, 0, null);
            RedrawCanvas(-1, showCollisionBoxes);
        } catch (Exception e) {
            ReportError("Error adding image to layer: " + e.getMessage());
        }
    }
    
    @SimpleFunction(description = "Replace an image in a specific layer.")
    public void ReplaceImageInLayer(String layerName, @Asset String imagePath) {
        try {
            if (!layerMap.containsKey(layerName)) {
                ReportError("Layer not found: " + layerName);
                return;
            }

            Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);
            if (imageBitmap == null) {
                Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
                imageBitmap = ((BitmapDrawable) drawable).getBitmap();
            }

            Layer layer = layerMap.get(layerName);
            
            // Reciclar o bitmap antigo antes de substituir
            recycleBitmap(layer.bitmap);
            
            // Limpa a camada antes de adicionar a nova imagem
            clearLayerBitmap(layer.bitmap);

            android.graphics.Canvas canvas = new android.graphics.Canvas(layer.bitmap);
            canvas.drawBitmap(imageBitmap, 0, 0, null); // Desenha a nova imagem
            RedrawCanvas(-1, showCollisionBoxes);
        } catch (Exception e) {
            ReportError("Error replacing image in layer: " + e.getMessage());
        }
    }

    private void clearLayerBitmap(Bitmap bitmap) {
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
    }
    
    /*
     * PARALLAX
     */
    
    @SimpleFunction(description = "Enable or disable the parallax effect.")
    public void SetParallaxEnabled(boolean enabled) {
        this.isParallaxEnabled = enabled;
        if (!enabled) {
            resetLayersToOriginalPosition(); // Método para resetar as camadas
        }
    }
    
    private void resetLayersToOriginalPosition() {
        for (Layer layer : layerMap.values()) {
            layer.parallaxIntensity = 0.0f;
            updateLayerParallax(layer);
        }
    }
    
    @SimpleFunction(description = "Apply a parallax effect to a specific layer.")
    public void CreateParallaxEffect(String layerName, float intensity) {
        if (!layerMap.containsKey(layerName)) {
            ReportError("Layer not found: " + layerName);
            return;
        }

        Layer layer = layerMap.get(layerName);
        layer.parallaxIntensity = intensity; // Armazena a intensidade na camada
        updateLayerParallax(layer); // Chama um método para atualizar o efeito de paralaxe na camada
    }

    private void updateLayerParallax(Layer layer) {
        Vector2D cameraPosition = camera.getPosition();

        // Calcula a translação com base na posição da câmera e na intensidade da camada
        float translationX = cameraPosition.x * layer.parallaxIntensity;
        float translationY = cameraPosition.y * layer.parallaxIntensity;

        Bitmap transformedBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(transformedBitmap);
        Matrix matrix = new Matrix();
        matrix.setTranslate(translationX, translationY);
        canvas.drawBitmap(layer.bitmap, matrix, null);

        layer.bitmap = transformedBitmap;
        RedrawCanvas(-1, showCollisionBoxes);
    }
    
    public void UpdateParallaxEffectRelativeToObject(int objectId) {
        PhysicsObject targetObject = objects.get(objectId);
        if (targetObject == null) {
            ReportError("Object not found: " + objectId);
            return;
        }

        Vector2D objectPosition = targetObject.getPosition();
        
        if (this.isParallaxEnabled) {
	        for (Layer layer : layerMap.values()) {
	            if (layer.parallaxIntensity > 0.0f) {
	                updateLayerParallaxRelativeToPosition(layer, objectPosition);
	            }
	        }
        }
    }

    private void updateLayerParallaxRelativeToPosition(Layer layer, Vector2D position) {
        if (!this.isParallaxEnabled) return;

        float translationX = -position.x * layer.parallaxIntensity;
        float translationY = -position.y * layer.parallaxIntensity;

        Bitmap transformedBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(transformedBitmap);
        Matrix matrix = new Matrix();
        matrix.setTranslate(translationX, translationY);
        canvas.drawBitmap(layer.bitmap, matrix, null);

        layer.bitmap = transformedBitmap;
        RedrawCanvas(-1, showCollisionBoxes);
    }
    
    /*
     * END PARALLAX
     */

    
    @SimpleFunction(description = "Move a layer up in the order of stacking.")
    public void MoveLayerUp(String layerName) {
        if (!layerMap.containsKey(layerName)) {
            ReportError("Layer not found: " + layerName);
            return;
        }

        Layer layerToMove = layerMap.get(layerName);
        for (String key : layerMap.keySet()) {
            Layer otherLayer = layerMap.get(key);
            if (otherLayer.zIndex == layerToMove.zIndex + 1) {
                otherLayer.zIndex--;
                layerToMove.zIndex++;
                RedrawCanvas(-1, showCollisionBoxes);
                return;
            }
        }
    }

    @SimpleFunction(description = "Move a layer down in the order of stacking.")
    public void MoveLayerDown(String layerName) {
        if (!layerMap.containsKey(layerName)) {
            ReportError("Layer not found: " + layerName);
            return;
        }

        Layer layerToMove = layerMap.get(layerName);
        for (String key : layerMap.keySet()) {
            Layer otherLayer = layerMap.get(key);
            if (otherLayer.zIndex == layerToMove.zIndex - 1) {
                otherLayer.zIndex++;
                layerToMove.zIndex--;
                RedrawCanvas(-1, showCollisionBoxes);
                return;
            }
        }
    }

    
    /*
     * END LAYERS
     */
        
    @SimpleFunction(description = "Set canvas dimensions.")
    public void SetCanvasSize(int newWidth, int newHeight, String layerName) {
        if (newWidth <= 0 || newHeight <= 0) {
            if (flagLog) Log.e(LOG_NAME, "Dimensões inválidas para o bitmap: Largura e altura devem ser positivas.");
            ReportError("Dimensões inválidas para o bitmap: Largura e altura devem ser positivas.");
            return;
        }

        try {
            // Obtém o bitmap da camada ativa
        	Layer layer = layerMap.get(layerName);
            // Cria um novo bitmap para aplicar a transformação
            Bitmap scaledBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(scaledBitmap);

            // Aplica a transformação paralaxe
            canvas.drawBitmap(layer.bitmap, 0, 0, null);

            // Atualiza o bitmap da camada com o bitmap transformado
            layer.bitmap = scaledBitmap;
            
            // Atualiza as variáveis de largura e altura do canvas
            canvasWidth = newWidth;
            canvasHeight = newHeight;

            // Redesenha o canvas
            RedrawCanvas(-1, showCollisionBoxes);
        } catch (Exception e) {
            if (flagLog) Log.e(LOG_NAME, "Erro ao redimensionar o bitmap: " + e.getMessage(), e);
            ReportError("Erro ao redimensionar o bitmap.");
        }
    }

    
    @SimpleFunction(description = "Get the bitmap of a specific layer by its name.")
    public String GetLayerBitmapAsPath(String layerName) {
        Bitmap layerBitmap = getLayerBitmap(layerName);
        if (layerBitmap != null) {
            try {
                // Salvar o bitmap em um arquivo temporário e retornar o caminho
                String path = container.$form().getExternalFilesDir(null).getAbsolutePath() + "/" + layerName + "_bitmap.png";
                OutputStream outputStream = new FileOutputStream(path);
                layerBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.close();
                return path;
            } catch (IOException e) {
                e.printStackTrace();
                ReportError("Erro ao obter bitmap da camada: " + e.getMessage());
                return "";
            }
        } else {
            // Retornar uma string vazia ou manipular o erro conforme necessário
            return "";
        }
    }
    
    /*
     * DISPLAY
     */
    @SimpleFunction(description = "Returns the width of the device screen in pixels.")
    public int GetScreenWidth() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        container.$context().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }

    @SimpleFunction(description = "Returns the height of the device screen in pixels.")
    public int GetScreenHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        container.$context().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels;
    }
    /*
     * DISPLAY
     */
    
    /*
     * collision box
     */
    @SimpleFunction(description = "Toggle the visibility of collision boxes for all objects.")
    public void ToggleCollisionBoxesVisibility(boolean showBox) {
        showCollisionBoxes = showBox;
        RedrawCanvas(-1, showCollisionBoxes);
    }

    
    @SimpleFunction(description = "Sets the position of a specific object's collision box in the active layer.")
    public void SetObjectCollisionBoxPosition(int objectId, float newX, float newY) {
        // Verifica se a camada ativa existe
        if (!layerMap.containsKey(activeLayerName)) {
            ReportError("Active layer not found.");
            return;
        }

        // Obtém o objeto e a camada ativa
        PhysicsObject obj = objects.get(objectId);
        if (obj == null) {
            ReportError("Object not found: " + objectId);
            return;
        }

        // Define a nova posição para o objeto
        obj.setPosition(new Vector2D(newX, newY));

        // Redesenha o canvas com as caixas de colisão atualizadas
        RedrawCanvas(objectId, showCollisionBoxes);
    }
    
    /*
     * collision box
     */    
    
    @SimpleFunction(description = "Triggers a timed event after a delay.")
    public void TriggerTimedEvent(final int id, final int triggerTimeMs) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                TimedEvent(id);
            }
        }, triggerTimeMs); // Atrasa por triggerTimeMs milissegundos
    }
    
    /*
     * CAMERA
     */
    @SimpleFunction(description = "Sets the camera position and follows an object by its ID.")
    public void SetCameraAndFollowObject(int objectId, float x, float y) {
        PhysicsObject targetObject = objects.get(objectId);

        if (targetObject != null) {
            camera.update(targetObject);
        } else {
            // Se não houver um objeto com a ID fornecida, apenas ajusta a posição da câmera
            camera.setPosition(x, y);
        }

        RedrawCanvas(-1, showCollisionBoxes);
    }

    
    @SimpleFunction(description = "Sets the camera position.")
    public void SetCameraPosition(float x, float y) {
        camera.setPosition(x, y);
        RedrawCanvas(-1, showCollisionBoxes);
    }

    @SimpleFunction(description = "Sets the camera zoom.")
    public void SetCameraZoom(float zoom) {
        camera.setZoom(zoom);
        RedrawCanvas(-1, showCollisionBoxes);
    }

    @SimpleFunction(description = "Gets the current camera position as a list [x, y].")
    public YailList GetCameraPosition() {
        Vector2D pos = camera.getPosition();
        return YailList.makeList(new Object[]{pos.x, pos.y});
    }

    @SimpleFunction(description = "Gets the current camera zoom.")
    public float GetCameraZoom() {
        return camera.getZoom();
    }
    /*
     * END CAMERA
     */
    
    @SimpleFunction(description = "Creates a tilemap by replicating an image nx by ny times.")
    public void CreateTileMap(String layerName, String imagePath, int nx, int ny) {
        try {
            if (!layerMap.containsKey(layerName)) {
                ReportError("Layer not found: " + layerName);
                return;
            }

            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap tileBitmap = ((BitmapDrawable) drawable).getBitmap();

            Layer layer = layerMap.get(layerName);
            android.graphics.Canvas canvas = new android.graphics.Canvas(layer.bitmap);

            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    canvas.drawBitmap(tileBitmap, x * tileBitmap.getWidth(), y * tileBitmap.getHeight(), null);
                }
            }

            RedrawCanvas(-1, showCollisionBoxes);
        } catch (Exception e) {
            ReportError("Error creating tilemap: " + e.getMessage());
        }
    }
    
    @SimpleFunction(description = "Oscillates an object horizontally.")
    public void OscillateObjectHorizontally(int objectId, float amplitude, long oscillationTime) {
        PhysicsObject obj = objects.get(objectId);
        if (obj != null) {
            obj.startOscillatingHorizontally(amplitude, oscillationTime);
        } else {
        	ReportError("Error! The object was not found!");
        }
    }

    @SimpleFunction(description = "Oscillates an object vertically.")
    public void OscillateObjectVertically(int objectId, float amplitude, long oscillationTime) {
        PhysicsObject obj = objects.get(objectId);
        if (obj != null) {
            obj.startOscillatingVertically(amplitude, oscillationTime);
        } else {
        	ReportError("Error! The object was not found!");
        }
    }
    
    @SimpleFunction(description = "Creates a new container with the specified parent object.")
    public void CreateContainer(int parentId) {
        PhysicsObject parent = objects.get(parentId);
        if (parent != null) {
            Container container = new Container(parent);
            containers.put(parentId, container);
        }
    }

    @SimpleFunction(description = "Adds a child object to the specified container.")
    public void AddChildToContainer(int containerId, int childId) {
        Container container = containers.get(containerId);
        PhysicsObject child = objects.get(childId);
        if (container != null && child != null) {
            container.addChild(child);
        }
    }

    @SimpleFunction(description = "Removes a child object from the specified container.")
    public void RemoveChildFromContainer(int containerId, int childId) {
        Container container = containers.get(containerId);
        PhysicsObject child = objects.get(childId);
        if (container != null && child != null) {
            container.removeChild(child);
        }
    }
    
    
    /*
     * Following
     */
    @SimpleFunction(description = "Make an object follow another object within a specific distance.")
    public void StartFollowing(int followerId, int leaderId, float maxFollowDistance, float stopFollowDistance) {
        followingObjects.put(followerId, new FollowInfo(followerId, leaderId, maxFollowDistance, stopFollowDistance));
    }

    private void updateFollowing(float deltaTime) {
        for (FollowInfo followInfo : followingObjects.values()) {
            PhysicsObject follower = objects.get(followInfo.getFollowerId());
            PhysicsObject leader = objects.get(followInfo.getLeaderId());

            if (follower == null || leader == null) {
                continue;
            }

            Vector2D followerPos = follower.getPosition();
            Vector2D leaderPos = leader.getPosition();
            float distance = Vector2D.distance(followerPos, leaderPos);

            if (distance <= followInfo.getMaxFollowDistance() && distance > followInfo.getStopFollowDistance()) {
                Vector2D direction = new Vector2D(leaderPos.x - followerPos.x, leaderPos.y - followerPos.y);
                direction.normalize();

                // Certifique-se de que getSpeed() está definido em PhysicsObject
                float speed = follower.getSpeed(); 

                follower.setVelocity(new Vector2D(direction.x * speed, direction.y * speed));
            } else if (distance > followInfo.getMaxFollowDistance()) {
                follower.setVelocity(new Vector2D(0, 0));
            }
        }
    }

    public void updateFollowingAsync(final float deltaTime) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                updateFollowing(deltaTime);
            }
        });
    }
    /*
     * END FOLLOWING
     */
    
    /*
     * This PredictFinalPositions method calculates and returns a YailList, where each item is another YailList containing the object's ID 
     * and its predicted final coordinates (x, y) after the specified time interval. It does this by applying the basic physics of uniformly 
     * accelerated motion, accounting for acceleration due to applied forces and gravity. 
     * This is a simplified example and does not take into account future collisions or changes in forces over time. 
     * For more complex scenarios, such as predicting collisions or movements caused by interactions between objects, 
     * you would need a more detailed simulation model.
     */
    /*
     * Trajectory preview
     */
    @SimpleFunction(description = "Predicts the final positions of all objects after a given time interval.")
    public YailList PredictFinalPositions(float timeInterval) {
        ArrayList<YailList> finalPositions = new ArrayList<>();
        for (Map.Entry<Integer, PhysicsObject> entry : objects.entrySet()) {
            int id = entry.getKey();
            PhysicsObject obj = entry.getValue();
            
            // Calcula a posição final baseada na velocidade atual e forças aplicadas
            Vector2D finalPosition = calculateFinalPosition(obj, timeInterval);
            
            // Adiciona a posição final à lista de resultados
            finalPositions.add(YailList.makeList(new Object[]{id, finalPosition.x, finalPosition.y}));
        }
        
        return YailList.makeList(finalPositions);
    }

    // Método auxiliar para calcular a posição final de um objeto
    private Vector2D calculateFinalPosition(PhysicsObject obj, float timeInterval) {
        Vector2D acceleration = new Vector2D(obj.getAppliedForce().x / obj.getMass(), obj.getAppliedForce().y / obj.getMass());
        acceleration.add(gravity); // Considera a gravidade
        
        // Calcula a posição final baseada na fórmula do movimento uniformemente acelerado
        float finalX = obj.getPosition().x + obj.getVelocity().x * timeInterval + 0.5f * acceleration.x * timeInterval * timeInterval;
        float finalY = obj.getPosition().y + obj.getVelocity().y * timeInterval + 0.5f * acceleration.y * timeInterval * timeInterval;
        
        return new Vector2D(finalX, finalY);
    }
    
    @SimpleFunction(description = "Predicts the velocity of an object after a given time interval.")
    public YailList PredictFinalVelocity(int objectId, float timeInterval) {
        PhysicsObject obj = objects.get(objectId);
        if (obj != null) {
            Vector2D acceleration = new Vector2D(obj.getAppliedForce().x / obj.getMass(), obj.getAppliedForce().y / obj.getMass());
            acceleration.add(gravity); // Considera a gravidade
            
            // Calcula a velocidade final
            float finalVx = obj.getVelocity().x + acceleration.x * timeInterval;
            float finalVy = obj.getVelocity().y + acceleration.y * timeInterval;
            
            return YailList.makeList(new Object[]{finalVx, finalVy});
        }
        return YailList.makeList(new Object[]{0f, 0f}); // Retorna velocidade zero se o objeto não for encontrado
    }
    
    /*
     * ANGLE
     */
    
    @SimpleFunction(description = "Calculates the angle of an object's velocity vector relative to the horizontal axis.")
    public float CalculateObjectAngle(int objectId) {
        PhysicsObject obj = objects.get(objectId);
        if (obj != null) {
            Vector2D velocity = obj.getVelocity();
            
            // Calcula o ângulo em radianos
            double angleRadians = Math.atan2(velocity.y, velocity.x);
            
            // Converte o ângulo para graus
            double angleDegrees = Math.toDegrees(angleRadians);
            
            // Normaliza o ângulo para um intervalo de 0 a 360 graus
            double normalizedAngle = (angleDegrees + 360) % 360;
            
            return (float)normalizedAngle;
        }
        return 0f; // Retorna 0 se o objeto não for encontrado
    }
    
    @SimpleFunction(description = "Inverts the direction of an object upon collision.")
    public void InvertObjectDirectionOnCollision(int objectId, String collisionSide) {
        PhysicsObject obj = objects.get(objectId);
        if (obj != null) {
            switch (collisionSide.toLowerCase()) {
                case "horizontal":
                    // Inverte a direção horizontal (eixo X)
                    obj.setVelocity(new Vector2D(-obj.getVelocity().x, obj.getVelocity().y));
                    break;
                case "vertical":
                    // Inverte a direção vertical (eixo Y)
                    obj.setVelocity(new Vector2D(obj.getVelocity().x, -obj.getVelocity().y));
                    break;
                case "both":
                    // Inverte ambas as direções
                    obj.setVelocity(new Vector2D(-obj.getVelocity().x, -obj.getVelocity().y));
                    break;
                default:
                    // Se o lado da colisão não for reconhecido, não faz nada
                    break;
            }
        }
    }

    @SimpleFunction(description = "Handles a collision by automatically detecting the collision side and inverting the object's direction accordingly.")
    public void HandleCollisionAndInvertDirection(int objectId1, int objectId2) {
        PhysicsObject obj1 = objects.get(objectId1);
        PhysicsObject obj2 = objects.get(objectId2);
        
        if (obj1 != null && obj2 != null) {
        	// Calcula o centro de massa de cada objeto
            Vector2D center1 = new Vector2D(obj1.getPosition().x + obj1.getSize().x / 2, obj1.getPosition().y + obj1.getSize().y / 2);
            Vector2D center2 = new Vector2D(obj2.getPosition().x + obj2.getSize().x / 2, obj2.getPosition().y + obj2.getSize().y / 2);
            
            // Calcula a diferença de posição entre os centros usando o método correto
            Vector2D delta = center1.subtract2D(center2);
            
            // Determina a direção da colisão com base na diferença de posição e velocidade dos objetos
            boolean horizontalCollision = Math.abs(delta.x) > Math.abs(delta.y);
            boolean fromLeftOrRight = obj1.getVelocity().x * delta.x > 0;
            boolean verticalCollision = !horizontalCollision;
            boolean fromTopOrBottom = obj1.getVelocity().y * delta.y > 0;
            
            if (horizontalCollision && fromLeftOrRight) {
                // Inverte a direção horizontal (eixo X)
                obj1.setVelocity(new Vector2D(-obj1.getVelocity().x, obj1.getVelocity().y));
            }
            if (verticalCollision && fromTopOrBottom) {
                // Inverte a direção vertical (eixo Y)
                obj1.setVelocity(new Vector2D(obj1.getVelocity().x, -obj1.getVelocity().y));
            }
            
            // Notifica a inversão da direção após a colisão
            OnDirectionInverted(objectId1, horizontalCollision ? "horizontal" : "vertical");
        }
    }
    
    /*
     * DYNAMICS SPRITES
     */
    @SimpleFunction(description = "Sets the origin point for a specific sprite.")
    public void SetSpriteOriginPoint(int spriteId, @Options(OriginPoint.class) String originPointStr, float customX, float customY) {
        Sprite sprite = sprites.get(spriteId);
        if (sprite != null) {
            // Usa o método fromUnderlyingValue para converter a string para o valor enum correspondente
            OriginPoint op = OriginPoint.fromUnderlyingValue(originPointStr);
            if (op != null) {
                sprite.setOriginPoint(op, new Vector2D(customX, customY));
            } else {
                // Opção para lidar com um valor de string inválido, por exemplo, logar um erro ou lançar uma exceção
                ReportError("Invalid origin point value: " + originPointStr);
            }
        }
    }
    
    @SimpleFunction(description = "Creates an ImageSprite on a specified layer with initial configurations.")
    public void CreateImageSpriteOnLayer(String layerName, @Options(OriginPoint.class) String originPointStr, @Asset String imagePath, int objectId, float x, float y, float width, float height, float mass, float friction) {
        if (!layerMap.containsKey(layerName)) {
            ReportError("Layer '" + layerName + "' not found.");
            return;
        }
        
        try {
            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap imageBitmap = ((BitmapDrawable) drawable).getBitmap();
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, (int)width, (int)height, true);
            
            // Cria um novo PhysicsObject para a sprite
            PhysicsObject physicsObject = new PhysicsObject(x, y, width, height, mass, friction); // Ajuste massa e atrito conforme necessário
            objects.put(objectId, physicsObject);
            
            // Cria e armazena o novo Sprite associado ao PhysicsObject
            Sprite sprite = new Sprite(resizedBitmap, physicsObject);
            sprites.put(objectId, sprite);

            // Define o ponto de origem da Sprite no centro
            OriginPoint op = OriginPoint.fromUnderlyingValue(originPointStr);
            sprite.setOriginPoint(op, null);

            // Ajusta a posição da Sprite para que o ponto de origem esteja nas coordenadas (x, y)
            float adjustedX = x - (width / 2);
            float adjustedY = y - (height / 2);
            physicsObject.setPosition(new Vector2D(adjustedX, adjustedY));
            
            RedrawCanvas(-1, showCollisionBoxes);
            // Não é mais necessário chamar RedrawCanvas aqui, a atualização será gerenciada pelo sistema de física
        } catch (Exception e) {
            ReportError("Error loading or scaling image: " + e.getMessage());
        }
    }
    
    // Classe auxiliar para gerenciamento de cache de bitmaps
    class BitmapCache {
        private LruCache<String, Bitmap> cache;

        public BitmapCache() {
            final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
            final int cacheSize = maxMemory / 8; // Usa 1/8 da memória disponível para cache

            cache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }
            };
        }

        public Bitmap getBitmapFromMemCache(String key) {
            return cache.get(key);
        }

        public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
            if (getBitmapFromMemCache(key) == null) {
                cache.put(key, bitmap);
            }
        }
    }
    
    @SimpleFunction(description = "Changes the image of a sprite based on its ID.")
    public void SetSpriteImage(final int spriteId, @Asset final String imagePath) {
    	final Sprite sprite = sprites.get(spriteId);
        if (sprite != null) {
            // Executa na thread da UI
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Bitmap newImageBitmap = bitmapCache.getBitmapFromMemCache(imagePath);
                    if (newImageBitmap == null) { // Se o bitmap não está no cache
                        try {
                            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
                            newImageBitmap = ((BitmapDrawable) drawable).getBitmap();
                            bitmapCache.addBitmapToMemoryCache(imagePath, newImageBitmap);
                        } catch (IOException e) {
                            e.printStackTrace();
                            ReportError("Error loading image: " + e.getMessage());
                            return;
                        }
                    }

                    // Recicla o bitmap antigo antes de substituir
                    if (sprite.getImage() != null && !sprite.getImage().isRecycled()) {
                        sprite.getImage().recycle();
                    }

                    // Atualiza a imagem da sprite
                    sprite.setImage(newImageBitmap);
                    RedrawCanvas(-1, showCollisionBoxes);
                }
            });
        } else {
            ReportError("Sprite not found with ID: " + spriteId);
        }
    }

    
    /**
     * Registra o toque na tela e verifica se ele interage com alguma sprite.
     */
    
    @SimpleFunction(description = "Intercepts touches on Sprites and optionally shows touch bounds in Dev mode.")
    public void RegisterTouchEvent(final float touchX, final float touchY, final boolean showTouches) {
    	Future<?> future = executorService.submit(new Runnable() {
            @Override
            public void run() {
                lastTouchPosition = new Vector2D(touchX, touchY);
                showTouchesAndBounds = showTouches;

                boolean touchDetected = false;
                for (Map.Entry<Integer, Sprite> entry : sprites.entrySet()) {
                    Sprite sprite = entry.getValue();
                    PhysicsObject physicsObject = sprite.getPhysicsObject();
                    Vector2D position = physicsObject.getPosition();
                    float width = physicsObject.getSize().x;
                    float height = physicsObject.getSize().y;

                    // Calcula os limites da sprite usando RectF
                    RectF spriteBounds = sprite.getBounds();

                    if (spriteBounds.contains(touchX, touchY)) {
                        final int spriteId = entry.getKey();
                        touchDetected = true;
                        // Chama o evento OnSpriteTouched na thread principal
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                OnSpriteTouched(spriteId);
                            }
                        });
                        break; // Para a iteração após o primeiro toque detectado para evitar múltiplos toques
                    }
                }

                if (showTouches && touchDetected) {
                    // Solicita um redesenho do canvas na thread principal
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            RedrawCanvas(-1, showCollisionBoxes);
                        }
                    });
                }
            }
        });
    }

    /**
     * Remove e destrói uma sprite baseada no seu ID. Atualiza o canvas para refletir a remoção.
     * 
     * @param spriteId O ID da sprite a ser removida.
     */
    @SimpleFunction(description = "Removes and destroys a sprite by its ID and updates the canvas.")
    public void RemoveSprite(int spriteId) {
        if (sprites.containsKey(spriteId)) {
            sprites.remove(spriteId); // Remove a sprite do mapa
            objects.remove(spriteId); // Remove o objeto de física associado
            RedrawCanvas(-1, showCollisionBoxes); // Atualiza o canvas para refletir a remoção
        } else {
            ReportError("Sprite not found with ID: " + spriteId);
        }
    }
    
    @SimpleFunction(description = "Registers a touch event on the canvas and displays touch coordinates and FPS.")
    public void RegisterTouchAndDisplayInfo(final float touchX, final float touchY) {
        touchEventExecutor.submit(new Runnable() {
            @Override
            public void run() {
                PhysicsEngine.this.touchX = touchX;
                PhysicsEngine.this.touchY = touchY;
                PhysicsEngine.this.showTouchInfo = true;
                updateFPS();

                // Post na thread da UI para atualizar o canvas
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                    	RedrawCanvas(-1, showCollisionBoxes);
                    }
                });

                // Espera 500 ms para limpar as informações
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                PhysicsEngine.this.showTouchInfo = false;

                // Post na thread da UI para remover as informações do canvas
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                    	RedrawCanvas(-1, showCollisionBoxes);
                    }
                });
            }
        });
    }
    
    private void updateFPS() {
        long currentTime = System.currentTimeMillis();
        fps = 1000f / (currentTime - lastTouchTime);
        lastTouchTime = currentTime;
    }
    
    

    /*
     * EVENTS
     */
    /**
     * Evento disparado quando uma sprite é tocada.
     * 
     * @param spriteId O ID da sprite que foi tocada.
     */
    @SimpleEvent(description = "Event triggered when a sprite is touched.")
    public void OnSpriteTouched(int spriteId) {
        EventDispatcher.dispatchEvent(this, "OnSpriteTouched", spriteId);
    }
    
    @SimpleEvent(description = "Event triggered when an object's direction is inverted after a collision.")
    public void OnDirectionInverted(int objectId, String collisionSide) {
        EventDispatcher.dispatchEvent(this, "OnDirectionInverted", objectId, collisionSide);
    }
    
    @SimpleEvent(description = "Triggered when the Canvas size changes.")
    public void CanvasSizeChanged(int width, int height) {
        EventDispatcher.dispatchEvent(this, "CanvasSizeChanged", width, height);
    }
    
    @SimpleEvent(description = "Event triggered when an animation frame changes.")
    public void OnAnimationFrameChanged(String objectId, String imagePath) {
        EventDispatcher.dispatchEvent(this, "OnAnimationFrameChanged", objectId, imagePath);
    }
    
    @SimpleEvent(description = "Triggered when two objects collide with the side of collision.\n"
    		+ "collisionSide = \"bottom\" or \"top\" or \"left\" or \"right\"")
    public void OnCollision(int objectId1, int objectId2, String collisionSide) {
        EventDispatcher.dispatchEvent(this, "OnCollision", objectId1, objectId2, collisionSide);
    }
    
    @SimpleEvent(description = "Event triggered when an object's position changes.")
    public void OnPositionChanged(int objectId, float x, float y) {
        EventDispatcher.dispatchEvent(this, "OnPositionChanged", objectId, x, y);
    }
    
    @SimpleEvent(description = "Evento chamado a cada atualização do ciclo de física.")
    public void OnUpdate() {
    	new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
            	EventDispatcher.dispatchEvent(PhysicsEngine.this, "OnUpdate");
            }
        });
    }
    
    @SimpleEvent(description = "Report an error with a custom message")
    public void ReportError(String errorMessage) {
        EventDispatcher.dispatchEvent(this, "ReportError", errorMessage);
    }
    
    @SimpleEvent(description = "Event triggered after a specified delay.")
    public void TimedEvent(int id) {
        EventDispatcher.dispatchEvent(this, "TimedEvent", id);
    }

        
    /*
     * PRIVATE METHODS
     */
    private void checkCollisions(PhysicsObject obj1, PhysicsObject obj2) {
    	if (obj1.collidesWith(obj2)) {
            String collisionSide = obj1.getCollisionSide(obj2);
            if (!obj1.isPlatform() && obj2.isPlatform()) {
                handlePlatformCollision(obj1, obj2, collisionSide);
            } else if (obj1.isPlatform() && !obj2.isPlatform()) {
                handlePlatformCollision(obj2, obj1, collisionSide);
            } else {
                handleCollision(obj1, obj2, collisionSide);
            }
        }
    }


    private void handlePlatformCollision(PhysicsObject obj, PhysicsObject platform, final String collisionSide) {
    	if (platform.isPlatform() && collisionSide.equals("bottom")) {
            obj.setOnPlatform(true);
            
            // Parar a velocidade vertical do objeto para evitar movimento contínuo para baixo
            obj.setVelocity(new Vector2D(obj.getVelocity().x, 0));
            
            // Ajustar a posição y do objeto para ficar exatamente em cima da plataforma
            float correctedY = platform.getPosition().y - platform.getSize().y;
            obj.setPosition(new Vector2D(obj.getPosition().x, correctedY));

            final int objId1 = findObjectId(obj);
            final int objId2 = findObjectId(platform);
            
            // Postar o evento de colisão para ser executado no thread da UI
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    OnCollision(objId1, objId2, collisionSide);
                }
            });
        } else {
            obj.setOnPlatform(false);
        }
    	
    	if (platform.isPlatform()) {
            if (collisionSide.equals("bottom")) {
        		
            } else {
                obj.setOnPlatform(false);
            }
        }
    }

    // Método para lidar com a colisão
    private void handleCollision(PhysicsObject obj1, PhysicsObject obj2, final String collisionSide) {
        final int objId1 = findObjectId(obj1);
        final int objId2 = findObjectId(obj2);
        if (objId1 != -1 && objId2 != -1) {
            // Postar o evento de colisão para ser executado no thread da UI
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    OnCollision(objId1, objId2, collisionSide);
                }
            });
        }
    }

    // Helper method to find object ID
    private int findObjectId(PhysicsObject obj) {
        for (Map.Entry<Integer, PhysicsObject> entry : objects.entrySet()) {
            if (entry.getValue().equals(obj)) {
                return entry.getKey();
            }
        }
        return -1; // Return -1 if object not found
    }
    
    public class Pair<F, S> {
        private F first;
        private S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }
    }
    
    private void updateOnPlatformState(PhysicsObject obj) {
        boolean onPlatform = false;
        for (PhysicsObject platform : objects.values()) {
            if (platform.isPlatform() && obj.collidesWith(platform)) {
                onPlatform = true;
                break;
            }
        }
        obj.setOnPlatform(onPlatform);
    }
    
    public void SetCanvasMonitoring(final Canvas canvas) {
        final View view = canvas.getView();

        // Posterga a verificação até que a View esteja completamente carregada
        view.post(new Runnable() {
            @Override
            public void run() {
                view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Remove o listener para evitar chamadas múltiplas
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } else {
                            view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }

                        final int newWidth = view.getWidth();
                        final int newHeight = view.getHeight();
                        
                        CanvasSizeChanged(newWidth, newHeight);
                        /*
                        // Atrasa a execução do evento CanvasSizeChanged
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                CanvasSizeChanged(newWidth, newHeight);
                            }
                        }, 500); // Atrasa por 500 milissegundos, ajuste conforme necessário
                        */
                    }
                });
            }
        });
    }
    
    private Bitmap getLayerBitmap(String layerName) {
        Layer layer = layerMap.get(layerName);
        if (layer != null) {
            return layer.bitmap;
        } else {
            // Manipular o caso em que a camada não é encontrada
        	if (flagLog) Log.e(LOG_NAME, "Camada não encontrada: " + layerName);
            return null;
        }
    }
    
    /*
     * REDRAW
     */
    public synchronized void RedrawCanvas(final int objectId, final boolean showCollisionBoxes) {
    	((Activity) context).runOnUiThread(new Runnable() {
            @Override
	            public void run() {
		    	synchronized (bitmapLock) {
		            if (finalBitmap == null || finalBitmap.getWidth() != canvasWidth || finalBitmap.getHeight() != canvasHeight) {
		                // Recicla o Bitmap antigo se não for nulo
		                if (finalBitmap != null) {
		                    finalBitmap.recycle();
		                }
		                finalBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
		            }
		        }
		    	
		    	android.graphics.Canvas finalCanvas = new android.graphics.Canvas(finalBitmap);
		        paint.reset(); // Reseta o Paint para reutilização
		
		        // Ordena e desenha as camadas
		        List<Layer> sortedLayers = new ArrayList<>(layerMap.values());
		        Collections.sort(sortedLayers, new Comparator<Layer>() {
		            @Override
		            public int compare(Layer l1, Layer l2) {
		                return Integer.compare(l1.zIndex, l2.zIndex);
		            }
		        });
		        
		        for (Layer layer : sortedLayers) {
		            finalCanvas.drawBitmap(layer.bitmap, 0, 0, null);
		        }
		
		        // Desenha as informações de toque e FPS se necessário
		        if (showTouchInfo) {
		        	paint.setColor(Color.BLACK);
		            paint.setTextSize(30); // Define o tamanho do texto
		            
		            finalCanvas.drawText("Touch: (" + touchX + ", " + touchY + ")", 10, 40, paint);
		            finalCanvas.drawText("FPS: " + String.format("%.2f", fps), 10, 80, paint);
		        }
		        
		        // Desenha as caixas de colisão se necessário
		        if (showCollisionBoxes) {
		            paint.setColor(Color.RED);
		            paint.setStyle(Paint.Style.STROKE);
		            paint.setStrokeWidth(2);
		
		            if (objectId != -1) {
		                // Desenha a caixa de colisão para um objeto específico
		                PhysicsObject specificObj = objects.get(objectId);
		                if (specificObj != null) {
		                    Vector2D position = specificObj.getPosition();
		                    Vector2D size = specificObj.getSize();
		                    finalCanvas.drawRect((float) position.x, (float) position.y, 
		                                        (float) (position.x + size.x), (float) (position.y + size.y), paint);
		                }
		            } else {
		                // Desenha caixas de colisão para todos os objetos
		            	//(this.objects) {
		                for (PhysicsObject obj : objects.values()) {
			                    Vector2D position = obj.getPosition();
			                    Vector2D size = obj.getSize();
			                    finalCanvas.drawRect((float) position.x, (float) position.y, 
			                                        (float) (position.x + size.x), (float) (position.y + size.y), paint);
		                }
		            	//}
		            }
		        }
		        
		        if (showTouchesAndBounds) {
		            // Visualiza os limites do último toque em modo Dev
		            paint.setColor(Color.GREEN);
		            finalCanvas.drawCircle((float) lastTouchPosition.x, (float) lastTouchPosition.y, 10, paint);
		        }
		        
		        // Aplica o zoom da câmera
		        finalCanvas.scale(camera.getZoom(), camera.getZoom());
		
		        // Aplica a posição da câmera
		        finalCanvas.translate(-camera.getPosition().x, -camera.getPosition().y);
		        
		        // Desenhar sprites
		        // Desenhar sprites e pontos de origem
		        //(this.sprites) {
		        for (Sprite sprite : sprites.values()) {
		            sprite.draw(finalCanvas); // Desenha a sprite
		
		            if (showCollisionBoxes) {
		                // Calcula o ponto de origem para a sprite atual
		                Vector2D originOffset = sprite.calculateOriginOffset();
		                Vector2D spritePosition = sprite.getPhysicsObject().getPosition();
		
		                // Calcula a posição real do ponto de origem no canvas
		                float originX = sprite.getOriginX();
		                float originY = sprite.getOriginY();
		
		                // Define a cor do Paint para azul antes de desenhar o ponto de origem
		                paint.setColor(Color.BLUE);
		                paint.setStyle(Paint.Style.FILL);
		                finalCanvas.drawCircle(originX, originY, 5, paint); // Desenha o ponto de origem como um círculo pequeno
		            }
		        }
		        //}
		
		        // Restaurar a cor e estilo do paint se necessário
		        if (showCollisionBoxes) {
		            paint.setColor(Color.RED);
		            paint.setStyle(Paint.Style.FILL);
		            finalCanvas.drawCircle(lastTouchPosition.x, lastTouchPosition.y, 10, paint);
		        }
		        
		        
			    canvasComponent.getView().setBackground(new BitmapDrawable(context.getResources(), finalBitmap));
			    canvasComponent.getView().invalidate();
       
            }
        });
    }
    /*
     * REDRAW
     */
    
    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

}