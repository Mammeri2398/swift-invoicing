package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * V4 : avance la séquence d'identité de profile au-delà des lignes existantes.
 *
 * V2 insère le profil Administrateur avec un id explicite (1), ce qui n'avance pas la séquence :
 * la première création de profil depuis l'écran obtenait l'id 1 et échouait (clé dupliquée).
 * Migration Java car la valeur dépend de MAX(id) et RESTART WITH n'accepte qu'une constante
 * (PostgreSQL comme H2).
 */
public class V4__profile_id_sequence extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            long next;
            try (ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM profile")) {
                rs.next();
                next = rs.getLong(1);
            }
            st.execute("ALTER TABLE profile ALTER COLUMN id RESTART WITH " + next);
        }
    }
}
