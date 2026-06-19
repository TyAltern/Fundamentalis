package me.tyalternative.fundamentalis.api.status;


import java.util.UUID;

/**
 * Instance immuable d'un {@link StatusEffectType} actif (ou en sommeil) sur
 * une entité, à un niveau et une échéance d'expiration donnés.
 *
 * <h2>File de priorité par niveau</h2>
 * Pour un même {@link StatusEffectType}, une entité ne peut avoir qu'un seul
 * palier <strong>visible/actif</strong> à la fois — le palier de niveau le
 * plus élevé parmi ceux non expirés. Les paliers de niveau inférieur ne sont
 * pas mis en pause : leur échéance d'expiration ({@link #expiresAtTick()})
 * est fixée une fois pour toutes au moment de leur application et continue de
 * s'écouler normalement même pendant qu'un palier supérieur masque leurs effets.
 * Ils redeviennent simplement actifs dès qu'aucun palier de niveau supérieur
 * n'est encore en vie.
 *
 * <p>Exemple : Force niveau 1 appliqué pour 60s à t=0 (expire à t=60), puis
 * Force niveau 2 appliqué pour 20s à t=10 (expire à t=30) :
 * <pre>
 * t=0  à t=10 : Force 1 est actif (Force 2 pas encore appliqué)
 * t=10 à t=30 : Force 2 est actif (palier supérieur), Force 1 continue de courir en arrière-plan
 * t=30        : Force 2 expire — Force 1 redevient actif, il lui reste 60-30 = 30s
 * t=30 à t=60 : Force 1 est actif jusqu'à sa propre expiration
 * </pre>
 * Toute la mécanique de sélection du palier actif est gérée côté
 * {@code fundamentalis-status} (le moteur) — l'API expose uniquement la donnée.
 *
 * <p>Une instance est <strong>immuable</strong> : changer l'état actif/en-sommeil
 * produit une nouvelle instance via {@link #asActive(boolean)}.
 *
 * @param id           identifiant unique de cette instance d'effet (généré à l'application)
 * @param entityId     UUID de l'entité affectée
 * @param type         le type d'effet
 * @param level        le niveau de ce palier (1 à {@link StatusEffectType#getMaxLevel()})
 * @param expiresAtTick tick serveur absolu auquel ce palier expire définitivement
 * @param sourceId     identifiant de la source ayant appliqué l'effet (UUID joueur, "spell:fireball"…), nullable
 * @param active       {@code true} si ce palier est actuellement le plus élevé en vie (donc visible/actif)
 */
public record ActiveStatusEffect(
        UUID              id,
        UUID              entityId,
        StatusEffectType  type,
        int               level,
        long              expiresAtTick,
        String            sourceId,
        boolean           active
) {

    public ActiveStatusEffect {
        if (id == null)       throw new IllegalArgumentException("id ne peut pas être null");
        if (entityId == null) throw new IllegalArgumentException("entityId ne peut pas être null");
        if (type == null)     throw new IllegalArgumentException("type ne peut pas être null");
        if (level < 1)        throw new IllegalArgumentException("level doit être ≥ 1");
    }

    /**
     * Crée une copie de cette instance avec un nouvel état actif/en-sommeil.
     *
     * @param isActive {@code true} pour marquer ce palier comme actif
     * @return une nouvelle instance immuable
     */
    public ActiveStatusEffect asActive(boolean isActive) {
        return new ActiveStatusEffect(id, entityId, type, level, expiresAtTick, sourceId, isActive);
    }

    /**
     * Calcule le nombre de ticks restants avant expiration, à partir du tick
     * serveur courant.
     *
     * @param currentTick le tick serveur actuel
     * @return les ticks restants, jamais négatif
     */
    public long remainingTicks(long currentTick) {
        return Math.max(0, expiresAtTick - currentTick);
    }

    /**
     * @param currentTick le tick serveur actuel
     * @return {@code true} si ce palier est expiré au tick donné
     */
    public boolean isExpired(long currentTick) {
        return currentTick >= expiresAtTick;
    }
}
