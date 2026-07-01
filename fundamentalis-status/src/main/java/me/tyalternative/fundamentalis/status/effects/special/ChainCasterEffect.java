package me.tyalternative.fundamentalis.status.effects.special;


import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffectTypes;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Effet "lanceur" du système Chain — pensé pour être appliqué comme
 * {@code onHitEffect} d'une arme, avec une durée très courte (voir
 * {@link StatusEffectTypes#CHAIN}). Sa seule responsabilité est de détecter
 * le coup qui vient de l'appliquer et de déclencher un groupe via
 * {@link ChainGroupRegistry#createGroup} — la gestion réelle du lien (durée,
 * partage de dégâts) est ensuite entièrement portée par
 * {@link ChainVictimEffect}, appliqué séparément à chaque membre du groupe,
 * avec sa propre durée — bien plus longue, voir {@link StatusEffectTypes#CHAIN_LINK}.
 *
 * <h2>Comment ça capture "qui vient d'être frappé" ?</h2>
 * {@code CustomWeapon#onHitEffect(DamageInfo)} est appelé par
 * {@code DamageManager} <strong>avant</strong> que le {@link PostDamageEvent}
 * du même coup ne soit lancé — même pile d'appel, entièrement synchrone. En
 * appliquant ce palier CHAIN pendant {@code onHitEffect}, {@link #onApply()}
 * s'exécute immédiatement ({@code StatusComponent} appelle {@code onApply()}
 * de façon synchrone dès que le palier devient actif) et a donc le temps de
 * s'enregistrer comme {@link Listener} <strong>avant</strong> que ce même
 * coup ne déclenche son propre {@link PostDamageEvent} — qui est alors
 * intercepté ici pour savoir précisément qui vient d'être touché.
 */
public class ChainCasterEffect extends StatusEffect implements Listener {

    private final Plugin plugin;
    private final ChainGroupRegistry registry;

    private boolean listening = false;
    private boolean triggered = false; // garde-fou : un seul déclenchement par instance/application

    public ChainCasterEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                             Plugin plugin, ChainGroupRegistry registry) {
        super(holder, statsComponent, meta);
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public void onApply() {
        if (listening) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listening = true;
    }

    @Override
    public void onTick() {
        // Rien à faire : ce palier est volontairement très court (StatusEffectTypes.CHAIN),
        // juste le temps d'intercepter le PostDamageEvent du coup qui vient de l'appliquer.
    }

    @Override
    public void onRemove() {
        if (!listening) return;
        HandlerList.unregisterAll(this);
        listening = false;
    }

    @EventHandler
    public void onPostDamage(PostDamageEvent event) {
        if (triggered) return; // ce palier ne doit déclencher qu'un seul groupe

        LivingEntity attacker = event.getAttacker();
        if (attacker == null || !attacker.getUniqueId().equals(getEntity().getUniqueId())) return;

        triggered = true;
        registry.createGroup(event.getVictim(), attacker, getLevel());

        // Le déclenchement est fait : on libère le listener tout de suite plutôt
        // que d'attendre l'expiration naturelle (courte, mais autant être propre).
        HandlerList.unregisterAll(this);
        listening = false;
    }
}