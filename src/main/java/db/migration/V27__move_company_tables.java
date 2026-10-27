package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;
import java.util.List;

/**
 * V27 : déplace les tables d'une société du schéma public vers company_1 (la société n° 1, créée par V26).
 *
 * PostgreSQL : ALTER TABLE ... SET SCHEMA garde les données, les contraintes, les index et les séquences
 * d'identité ; les migrations de db/tenant reprennent ensuite le schéma à sa version de base (1).
 * H2 (les tests, toujours sur une base neuve) ne sait pas déplacer une table : elles sont supprimées
 * (elles ne contiennent que la ligne émetteur de V1) et db/tenant V1 les recrée dans company_1.
 */
public class V27__move_company_tables extends BaseJavaMigration {

    /** Every table of a company; children before parents (for H2's DROP). */
    static final List<String> COMPANY_TABLES = List.of(
        "recurring_invoice_line", "recurring_invoice", "invoice_event", "invoice_payment", "invoice_line", "invoice",
        "client", "catalog_item", "invoice_sequence", "credit_note_sequence", "document_sequence", "issuer",
        "mail_settings", "backup_settings", "accounting_settings");

    @Override
    public void migrate(Context context) throws Exception {
        boolean postgres = context.getConnection().getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        try (Statement st = context.getConnection().createStatement()) {
            if (postgres) {
                st.execute("CREATE SCHEMA IF NOT EXISTS company_1");
                for (String table : COMPANY_TABLES) {
                    st.execute("ALTER TABLE public." + table + " SET SCHEMA company_1");
                }
            } else {
                for (String table : COMPANY_TABLES) {
                    st.execute("DROP TABLE " + table + " CASCADE");
                }
            }
        }
    }
}
