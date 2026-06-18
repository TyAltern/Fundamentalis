package me.tyalternative.fundamentalis.api.exception;

/**
 * Lancée quand on utilise un identifiant de stat inconnu du registre.
 * <p>
 * Cela indique généralement une faute de frappe dans un fichier de
 * config ou un plugin qui n'a pas appelé StatTypeRegistry.register()
 * à son onEnable.
 */
public class StatTypeNotRegisteredException extends RuntimeException {

    private final String statId;

    public StatTypeNotRegisteredException(String statId) {
        super("StatType non enregistré : '" + statId + "'. Vérifiez que le plugin propriétaire de cette stat est bien chargé.");

        this.statId = statId;
    }

    public String getStatId() {
        return statId;
    }
}
