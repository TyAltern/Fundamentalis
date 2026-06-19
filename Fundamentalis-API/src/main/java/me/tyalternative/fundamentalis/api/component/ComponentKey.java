package me.tyalternative.fundamentalis.api.component;

import java.util.Objects;

/**
 * <H2>Clé typée identifiant un type de composant dans un {@link ComponentHolder}.</H2>
 *
 * L'utilisation d'une clé typée (plutôt que Class brute) permet au
 * compilateur de garantir la cohérence du type retourné par
 * ComponentHolder.get(key) sans cast non sécurisé.
 *<p>
 * <H3>Exemple de déclaration dans une interface de composant :</H3>
 *<pre>{@code
 *   public interface IStatsComponent extends Component {
 *       ComponentKey<IStatsComponent> KEY =
 *           ComponentKey.of("fundamentalis:stats", IStatsComponent.class);
 *       ...
 *   }
 *   }
 *<p>
 * <H3>Exemple d'utilisation :</H3>
 *<pre>{@code
 *   // Requête typée — pas de cast, pas de ClassCastException possible
 *   Optional<IStatsComponent> stats = holder.get(IStatsComponent.KEY);
 *}
 * La clé est identifiée par son namespaced id {@code (namespace:nom)}. <br>
 * Convention : {@code "<plugin_id>:<nom_du_composant>"} en lowercase. <br>
 * <p>
 * Ex : {@code "fundamentalis:stats"}, {@code "fundamentalis:health"}, {@code "myplugin:mana"}
 */
public record ComponentKey<C extends Component> (
        String   id,         // Ex : "fundamentalis:stats"
        Class<C> type        // Interface du composant
) {


    public ComponentKey {
        Objects.requireNonNull(id,   "L'id d'une ComponentKey ne peut pas être null");
        Objects.requireNonNull(type, "Le type d'une ComponentKey ne peut pas être null");
        if (!id.contains(":"))
            throw new IllegalArgumentException(
                    "Une ComponentKey doit être au format 'namespace:nom', reçu : '" + id + "'");
    }

    /**
     * Factory method — point d'entrée recommandé.
     *
     * @param id   Identifiant namespaced, ex : "fundamentalis:stats".
     * @param type Interface du composant.
     */
    public static <C extends Component> ComponentKey<C> of(String id, Class<C> type) {
        return new ComponentKey<>(id.toLowerCase(), type);
    }

    @Override
    public String toString() {
        return "ComponentKey(" + id + ")";
    }
}
