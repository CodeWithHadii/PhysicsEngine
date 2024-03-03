package com.bosonshiggs.physicsengine.helpers;

/**
 * Classe para armazenar informações de seguimento de um objeto por outro.
 */
public class FollowInfo {
    private int followerId;
    private int leaderId;
    private float maxFollowDistance;
    private float stopFollowDistance;

    /**
     * Construtor para criar uma nova instância de FollowInfo.
     * 
     * @param followerId ID do objeto seguidor.
     * @param leaderId ID do objeto líder.
     * @param maxFollowDistance A distância máxima para o seguidor começar a seguir.
     * @param stopFollowDistance A distância na qual o seguidor deve parar de seguir.
     */
    public FollowInfo(int followerId, int leaderId, float maxFollowDistance, float stopFollowDistance) {
        this.followerId = followerId;
        this.leaderId = leaderId;
        this.maxFollowDistance = maxFollowDistance;
        this.stopFollowDistance = stopFollowDistance;
    }

    // Métodos getters para acessar os campos privados
    public int getFollowerId() {
        return followerId;
    }

    public int getLeaderId() {
        return leaderId;
    }

    public float getMaxFollowDistance() {
        return maxFollowDistance;
    }

    public float getStopFollowDistance() {
        return stopFollowDistance;
    }
}
