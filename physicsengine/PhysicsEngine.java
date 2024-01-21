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

import java.util.HashMap;
import java.util.Map;

import java.util.List;
import java.util.ArrayList;

import java.util.Comparator;
import java.util.Collections;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.TimeUnit;

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

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;

import android.util.Log;

@DesignerComponent(
	    version = 1,
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

    private Vector2D gravity = new Vector2D(0, 9.8f);
    
    private String LOG_NAME = "PhysicsEngine";
    private boolean flagLog = true;
    
    // Pool de threads para melhorar a eficiência
    private ExecutorService collisionExecutor = Executors.newSingleThreadExecutor();    
    
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
    
    public PhysicsEngine(ComponentContainer container) {
        super(container.$form());
        this.container = container;
        this.context = container.$context();
        
        
     // Inicialize a câmera com valores padrão
        this.camera = new Camera(0, 0, 1.0f); // Posição (0,0) com zoom padrão 1
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
    
    @SimpleFunction(description = "Starts periodic updates for the physics simulation.")
    public void StartUpdates(int updateTimeMs) {
        setupPeriodicUpdate(updateTimeMs);
    }

    @SimpleFunction(description = "Stops periodic updates for the physics simulation.")
    public void StopUpdates() {
        updateHandler.removeCallbacks(updateTask);
    }

    private void setupPeriodicUpdate(final int updateTime) {
        updateTask = new Runnable() {
            @Override
            public void run() {
                update(updateTime/1000.0f); // Chama o método update com 10 milissegundos (0.01 segundos)
                updateHandler.postDelayed(this, updateTime); // Reagenda o mesmo runnable para executar após 10 milissegundos
            }
        };
        updateHandler.post(updateTask); // Inicia as atualizações periódicas
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
    public void update(float deltaTimeMs) {
        final float deltaTime = deltaTimeMs / 1000;

        try {
            synchronized (this.objects) {
                for (PhysicsObject obj : objects.values()) {
                    updateObject(obj, deltaTime);
                }
            }
            
         // Atualiza o efeito de paralaxe para todas as camadas
            if (this.isParallaxEnabled) {
	            for (Layer layer : layerMap.values()) {
	                if (layer.parallaxIntensity > 0.0f) {
	                    updateLayerParallax(layer);
	                }
	            }
            }
            
            OnUpdate();
        } catch (Exception e) {
            Log.e(LOG_NAME, "Erro na atualização da física: " + e.getMessage());
        }
    }

    private void updateObject(PhysicsObject obj, float deltaTime) {
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
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                OnPositionChanged(objectId, newPosition.x, newPosition.y);
            }
        });
        
        // Se as caixas de colisão estão ativadas, redesenha o canvas com elas
        if (showCollisionBoxes) {
        	RedrawCanvas(-1, true);
        }
        
        if (this.isParallaxEnabled) {
            UpdateParallaxEffectRelativeToObject(objectId);
        }
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

            Log.d(LOG_NAME, "Salto iniciado com força: " + jumpForce);

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_NAME, "Finalizando salto para o objeto: " + id);
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
            obj.setPosition(new Vector2D(x, y));
            updateOnPlatformState(obj);
            
            // Atualiza o efeito de paralaxe relativo ao objeto
            UpdateParallaxEffectRelativeToObject(id);
            
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
    public String MirrorImageHorizontally(@Asset String imagePath) {
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
    public String MirrorImageVertically(@Asset String imagePath) {
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
    public void StartAnimation(YailList imagePathsList, final int frameDurationMs, final String objectId) {
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
    public boolean IsAnimationActive(String objectId) {
        return animationTasks.containsKey(objectId);
    }
    
    @SimpleFunction(description = "Stop the animation with the specified ID.")
    public void StopAnimation(String objectId) {
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

    /*
     * EVENTS
     */
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
            Log.e(LOG_NAME, "Camada não encontrada: " + layerName);
            return null;
        }
    }
    
    /*
     * REDRAW
     */
    public void RedrawCanvas(int objectId, boolean showCollisionBoxes) {
        Bitmap finalBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas finalCanvas = new android.graphics.Canvas(finalBitmap);
        Paint paint = new Paint();

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
                for (PhysicsObject obj : objects.values()) {
                    Vector2D position = obj.getPosition();
                    Vector2D size = obj.getSize();
                    finalCanvas.drawRect((float) position.x, (float) position.y, 
                                        (float) (position.x + size.x), (float) (position.y + size.y), paint);
                }
            }
        }
        
        // Aplica o zoom da câmera
        finalCanvas.scale(camera.getZoom(), camera.getZoom());

        // Aplica a posição da câmera
        finalCanvas.translate(-camera.getPosition().x, -camera.getPosition().y);

        canvasComponent.getView().setBackground(new BitmapDrawable(context.getResources(), finalBitmap));
        canvasComponent.getView().invalidate();
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