# Swift Invoicing

Application de facturation pour auto-entrepreneur (titulaire d'une carte ANAE), Algérie :
clients, factures numérotées sans trou, PDF, envoi par email, exports pour le comptable et suivi
du chiffre d'affaires face au plafond annuel.

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Stack technique](#stack-technique)
- [Démarrer en développement](#démarrer-en-développement)
- [Profils dev / prod](#profils-dev--prod)
- [Mise en production](#mise-en-production)
- [Variables d'environnement](#variables-denvironnement)
- [Réglages applicatifs](#réglages-applicatifs)
- [Sécurité](#sécurité)
- [Règles métier](#règles-métier)
- [Tests](#tests)
- [Structure du projet](#structure-du-projet)

## Fonctionnalités

- **Factures** : brouillons modifiables, émission avec numéro `FA-2026-0001` (continu, sans trou, par
  année), paiement, annulation. Les factures émises sont figées : coordonnées de l'émetteur et du
  client copiées au moment de l'émission.
- **PDF** avec logo de l'émetteur, montant en lettres et mention « TVA non applicable ». Police
  embarquée (DejaVu Sans) : les noms en arabe s'impriment correctement.
- **Envoi par email** d'une facture émise ou payée, PDF joint, depuis la page de la facture.
- **Historique** de chaque facture : qui l'a créée, émise, payée, annulée ou envoyée, et quand.
- **Exports CSV et Excel** de la liste des factures, selon les filtres appliqués.
- **Chiffre d'affaires** (`/turnover`) : totaux de l'année, détail par mois, cumul, jauge du plafond
  annuel. Le plafond se règle dans **Paramètres**.
- **Vérification publique** (`/invoices/lookup`) : confirme qu'un numéro de facture existe, avec son
  statut et sa date, sans révéler le client ni le montant.
- **Utilisateurs, profils et privilèges**, double authentification par application.

## Stack technique

- Java 17, Maven (wrapper `./mvnw` fourni)
- Spring Boot 4, Spring Security 7, Spring Data JPA (Hibernate), Bean Validation, Thymeleaf
- PostgreSQL et Flyway : le schéma n'est créé que par les migrations (`db/migration`,
  `ddl-auto=validate`)
- PDF : template Thymeleaf rendu par openhtmltopdf ; arabe mis en forme par ICU4J
- Excel : Apache POI ; QR codes : ZXing
- CSS : Tailwind CSS, compilé par le CLI autonome pendant le build Maven (pas de Node.js requis)
- Interface en français ; montants en DZD, au format `1 234 567,50 DA`

## Démarrer en développement

Prérequis : JDK 17 et une base PostgreSQL. Le `docker-compose.yml` fourni en démarre une
(base, utilisateur et mot de passe `invoicing`) :

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run
```

Puis ouvrir http://localhost:7070.

Au premier démarrage, Flyway crée les tables et le profil émetteur (valeurs de remplacement à
compléter dans **Paramètres**), puis l'application crée le compte `admin` avec un **mot de passe
temporaire écrit une seule fois dans les logs** (ou celui de `INITIAL_ADMIN_PASSWORD`). Il doit
être changé à la première connexion.

Le premier build télécharge le CLI Tailwind depuis GitHub (somme SHA-256 vérifiée) et le garde dans
le dépôt Maven local. Après avoir ajouté des classes Tailwind dans un template, recompiler la CSS :

```bash
./mvnw generate-resources
```

## Profils dev / prod

| | `dev` (par défaut) | `prod` (image Docker) |
|---|---|---|
| Base de données | PostgreSQL local, `invoicing` / `invoicing` | `DB_URL`, `DB_USER`, `DB_PASSWORD` obligatoires |
| Emails | désactivés : les liens de réinitialisation sont écrits dans les logs | obligatoires (`MAIL_*`) |
| Cookie de session | envoyé aussi en HTTP | `Secure` (HTTPS uniquement) |
| Templates | relus à chaque requête | en cache |
| Double authentification | facultative pour tous | obligatoire pour les administrateurs |

Le profil se choisit avec `SPRING_PROFILES_ACTIVE`. En `prod`, l'application **refuse de démarrer**
si un réglage obligatoire manque ou est dangereux (variable absente, emails désactivés, URL
publique en `localhost`…) et liste tous les problèmes d'un coup (`ProductionSettingsCheck`).

## Mise en production

L'image Docker compile l'application, la CSS, et tourne avec le profil `prod`, sous un utilisateur
non root :

```bash
docker build -t swift-invoicing .
```

```bash
docker run -d -p 7070:7070 \
  -e DB_URL=jdbc:postgresql://db:5432/invoicing -e DB_USER=invoicing -e DB_PASSWORD=... \
  -e APP_BASE_URL=https://factures.example.dz \
  -e MAIL_HOST=smtp.example.dz -e MAIL_PORT=587 -e MAIL_USERNAME=factures@example.dz -e MAIL_PASSWORD=... \
  -e INITIAL_ADMIN_PASSWORD=... \
  swift-invoicing
```

- Servir l'application en **HTTPS** derrière un reverse proxy (nginx, Caddy, Traefik…). Les
  en-têtes `X-Forwarded-*` ne sont acceptés que depuis les adresses de réseau interne.
- Santé : `/actuator/health` (ainsi que `/liveness` et `/readiness`), statut seul, sans détail.
  L'image l'utilise comme `HEALTHCHECK`.
- Arrêt propre : les requêtes en cours se terminent avant l'arrêt.
- Le compte `admin` créé au premier démarrage doit changer son mot de passe, puis configurer la
  double authentification avant d'accéder au reste de l'application.
- Sauvegarder la base PostgreSQL : elle contient tout, y compris le logo de l'émetteur.

## Variables d'environnement

| Variable | Obligatoire en prod | Défaut | Rôle |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | | `dev` (`prod` dans l'image) | Profil actif |
| `DB_URL` | oui | dev : `jdbc:postgresql://localhost:5432/invoicing` | URL JDBC PostgreSQL |
| `DB_USER` | oui | dev : `invoicing` | Utilisateur de la base |
| `DB_PASSWORD` | oui | dev : `invoicing` | Mot de passe de la base |
| `APP_BASE_URL` | oui | dev : `http://localhost:7070` | URL publique, utilisée dans les liens de réinitialisation (HTTPS attendu) |
| `MAIL_ENABLED` | doit rester `true` | prod : `true`, dev : `false` | Envoi réel des emails |
| `MAIL_HOST` | en pratique (non vérifié au démarrage) | `localhost` | Serveur SMTP |
| `MAIL_PORT` | | `25` | Port SMTP |
| `MAIL_USERNAME` | selon le serveur | | Compte SMTP |
| `MAIL_PASSWORD` | selon le serveur | | Mot de passe SMTP |
| `MAIL_FROM` | si pas de `MAIL_USERNAME` | `MAIL_USERNAME` | Expéditeur des factures envoyées aux clients |
| `INITIAL_ADMIN_PASSWORD` | | aléatoire, écrit dans les logs | Mot de passe temporaire du compte `admin` créé au premier démarrage |
| `APP_TIME_ZONE` | | `Africa/Algiers` | Fuseau horaire de l'activité : date du jour, année de numérotation, retards (indépendant de celui du serveur) |
| `SESSION_COOKIE_SECURE` | | prod : `true`, dev : `false` | Cookie de session réservé à HTTPS |
| `SERVER_PORT` | | `7070` | Port HTTP |
| `TWO_FACTOR_REQUIRED_FOR` | | dev seulement, vide | Rend la double authentification obligatoire en dev pour ces privilèges |

Les factures envoyées par email partent de `MAIL_FROM` sous le nom commercial de l'émetteur ; les
réponses des clients arrivent à l'adresse email du profil émetteur (`Reply-To`).

## Réglages applicatifs

Dans `src/main/resources/application.properties` :

| Propriété | Défaut | Rôle |
|---|---|---|
| `invoice.number-prefix` | `FA` | Préfixe des numéros de facture |
| `invoice.vat-mention` | `TVA non applicable` | Mention imprimée sur les factures |
| `app.security.password.min-length` / `max-length` | `8` / `128` | Longueur des mots de passe |
| `app.security.lockout.max-attempts` / `duration` | `5` / `15m` | Verrouillage après échecs de connexion |
| `app.security.reset-token-validity` | `60m` | Validité d'un lien de réinitialisation |
| `app.security.rate-limit.login.*` | 10 / 5 min | Connexions et codes de double authentification par adresse IP |
| `app.security.rate-limit.password-reset.*` | 5 / 15 min | Demandes de lien et nouveaux mots de passe par adresse IP |
| `app.security.rate-limit.lookup.*` | 30 / 1 min | Vérifications publiques de facture par adresse IP |
| `app.security.two-factor.required-for` | `MANAGE_USERS,MANAGE_PROFILES,MANAGE_PRIVILEGES` | Privilèges qui imposent la double authentification (vide : facultative) |
| `app.security.two-factor.issuer` | `Swift Invoicing` | Nom du compte dans l'application d'authentification |

Le **plafond annuel de chiffre d'affaires** ne se règle plus ici : il se modifie dans
**Paramètres → Plafond annuel** (5 000 000 DA au départ). Vérifiez le montant en vigueur auprès de
l'ANAE ou de votre comptable.

## Sécurité

- Toutes les pages demandent une connexion, sauf la connexion, la réinitialisation du mot de passe,
  la vérification publique de facture et le endpoint de santé.
- Chaque action exige un **privilège** (`@PreAuthorize`). Un **profil** regroupe des privilèges, et
  chaque utilisateur a un profil. Le profil `Administrateur` a tous les privilèges.
  Gestion : `/users` (`MANAGE_USERS`), `/profiles` (`MANAGE_PROFILES`), `/privileges` (`MANAGE_PRIVILEGES`).
- Mots de passe hachés en **Argon2** ; au moins une majuscule, une minuscule et un chiffre, sans le
  nom d'utilisateur. Un mot de passe temporaire (création de compte, réinitialisation par un
  administrateur) doit être changé à la connexion suivante.
- **Verrouillage** du compte 15 minutes après 5 échecs consécutifs, et **limites par adresse IP**
  (HTTP 429 au-delà) sur la connexion, la réinitialisation et la vérification publique.
- **Double authentification** (`/account/two-factor`) : code à 6 chiffres d'une application
  (Google Authenticator, Microsoft Authenticator, FreeOTP…), configurée par QR code, avec 10 codes de
  secours à usage unique. Obligatoire pour les administrateurs en production. Un administrateur peut
  la réinitialiser pour un utilisateur qui a perdu son téléphone (page **Utilisateurs**).
- Sessions : expiration après **20 minutes** d'inactivité, nouvel identifiant à la connexion, autres
  sessions fermées après un changement de mot de passe ou l'activation de la double authentification.
  Pas de « se souvenir de moi ».
- En-têtes : CSP stricte (aucun script ni ressource externe), `X-Frame-Options: DENY`, politique de
  référent, protection CSRF sur tous les formulaires.

## Règles métier

- Numérotation `FA-{année}-{séquence sur 4 chiffres}`, continue et sans trou, repartant à 1 chaque
  année. Le compteur est verrouillé pendant l'émission : deux émissions simultanées ne peuvent pas
  obtenir le même numéro.
- Seuls les brouillons se modifient ou se suppriment. Une facture émise peut être marquée payée,
  annulée ou envoyée par email ; une facture annulée garde son numéro, jamais réutilisé.
- Client professionnel : NIF obligatoire avant l'émission. Au moins une ligne par facture.
- Pas de TVA : un seul total, avec la mention configurée.
- Chiffre d'affaires = factures émises et payées, par année d'émission ; les annulées ne comptent
  pas. Alerte à 80 % puis à 100 % du plafond, sur le tableau de bord et la page Chiffre d'affaires.

*Les mentions légales obligatoires sont à vérifier auprès de la DGI, de l'ANAE ou d'un expert-comptable ;
elles se modifient dans le template `templates/invoice/pdf.html` et dans `application.properties`.*

## Tests

```bash
./mvnw test
```

Les tests tournent sur H2 en mode PostgreSQL. Les tests `Postgres*` démarrent un vrai PostgreSQL
avec Testcontainers ; ils sont ignorés automatiquement si Docker n'est pas disponible.

Ils couvrent notamment :
- la numérotation, y compris sous émissions concurrentes ;
- le contenu des PDF (arabe compris) ;
- l'historique, les exports, le chiffre d'affaires ;
- l'envoi d'emails, avec un serveur SMTP en mémoire (GreenMail) ;
- la sécurité : privilèges, verrouillage, limites par IP, réinitialisation du mot de passe,
  double authentification (vecteurs de test de la RFC 6238) ;
- le rendu de toutes les pages ;
- les migrations Flyway.

## Structure du projet

Code organisé par fonctionnalité, sous `src/main/java/com/swiftlabs/invoicing/` :

| Paquet | Contenu |
|---|---|
| `invoice` | Factures, lignes, numérotation, PDF, historique, exports, envoi par email, vérification publique |
| `client` | Clients |
| `issuer` | Profil émetteur, logo, plafond annuel |
| `dashboard` | Tableau de bord et page Chiffre d'affaires |
| `access` | `auth` (connexion, verrouillage, limites par IP), `twofactor`, `user`, `profile`, `privilege`, `account` |
| `config` | Sécurité, propriétés, vérification des réglages de production |
| `common` | Montants, montant en lettres, erreurs, pagination |

Templates Thymeleaf dans `src/main/resources/templates/` (même découpage), sources CSS dans
`src/main/frontend/`, migrations dans `src/main/resources/db/migration/`.
