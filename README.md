# Open ENT — module `caliper` (Caliper Analytics 1.2)

Module Vert.x qui donne à Open ENT les deux rôles de
[1EdTech Caliper Analytics 1.2](https://www.1edtech.org/standards/caliper) : **point de dépôt**
d'événements d'apprentissage, et **capteur** de ses propres événements vers des destinations
tierces.

| Spécification | Support |
|---|---|
| Endpoint : réception d'enveloppes, jeton porteur | ✅ `POST /ims/caliper/v1p2/events` |
| Vérification complète de l'enveloppe et de chaque événement | ✅ refus en entier, rang et raison |
| Relecture des événements reçus | ✅ `GET /ims/caliper/v1p2/events` |
| Capteur : accès et connexions de l'ENT → Caliper | ✅ `ToolUseEvent`, `SessionEvent` |
| Profils Assessment, Grading, Forum, Media… | ❌ l'ENT n'enregistre pas ces événements |

Contexte et feuille de route : [`docs/international/interoperabilite-1edtech.md`](../../docs/international/interoperabilite-1edtech.md).

## Ce que le capteur traduit, et ce qu'il écarte

La source est `events.access_events`, la table du magasin d'événements d'entcore. Deux types y
ont un équivalent Caliper, et deux seulement :

| Événement de l'ENT | Événement Caliper |
|---|---|
| `ACCESS`, `ACCESS_ADAPTER` | `ToolUseEvent` / `Used`, objet = le module |
| `LOGIN` | `SessionEvent` / `LoggedIn`, objet = la plateforme |
| `ACTIVATION`, `SMS`, échecs de connexion | **comptés et écartés** |

Ce n'est pas un oubli. Le vocabulaire d'actions de Caliper est **fermé**, et aucun de ses profils
ne décrit l'activation d'un compte ou un échec d'authentification. Leur coller une action
approchante produirait des événements conformes en apparence, que les magasins compteraient avec
les autres — un faux qui ne se voit pas. Les écarter **en les comptant** laisse le compte juste
et le manque visible : le rapport du capteur dit exactement combien de quoi n'est pas parti.

Les profils plus riches de Caliper — évaluation, notation, forum, média — ne sont pas
implémentés parce que **l'ENT n'enregistre pas ces événements** : ce sont les modules qui
devraient les émettre. L'ajouter est un chantier par module, pas une rustine ici.

## Trois propriétés du capteur

**Le curseur n'avance qu'après une transmission réussie.** Une destination momentanément
injoignable ne coûte rien : le lot repart au tour suivant. Avancer d'abord perdrait
silencieusement tout ce qui était en vol.

**Chaque destination a son propre curseur.** Deux destinations avancent indépendamment, et l'une
en panne ne retient pas l'autre.

**Un doublon est reconnu, pas compté.** Chaque événement porte un UUID stable, et le dépôt est
idempotent (`ON CONFLICT DO NOTHING`) : un capteur qui réémet après un incident de réseau ne
fausse pas les courbes. Compter deux fois est aussi grave que perdre.

## Une enveloppe fautive est refusée en entier

Pas par rigidité. Un magasin qui accepte une enveloppe en écartant silencieusement deux
événements produit des comptes faux que personne ne remarquera ; une enveloppe refusée, avec le
**rang et la raison** de l'événement en cause, est renvoyée par le capteur après correction.
Sans le rang, un capteur qui envoie cinq cents événements ne saurait pas lequel corriger.

Trois exigences de la norme que la vérification applique à la lettre :

- l'enveloppe porte **exactement quatre propriétés** — `sensor`, `sendTime`, `dataVersion`,
  `data` — et aucune autre : « custom properties are not permitted » ;
- `dataVersion` vaut l'IRI de contexte, `http://purl.imsglobal.org/ctx/caliper/v1p2` ;
- **chaque événement porte son propre `@context`**, même dans une enveloppe qui déclare déjà sa
  version : chaque élément de `data` doit être un document JSON-LD autonome.

Et une règle qui n'est pas dans la norme mais qui évite des faux : une **action doit être admise
pour son type d'événement**. Un `ToolUseEvent` ne connaît que `Used`, un `SessionEvent` que
`LoggedIn`, `LoggedOut` et `TimedOut`.

## Architecture

```
fr.openent.caliper
├── Caliper.java             BaseServer : schéma SQL, contrôleurs, démarrage du capteur
├── CaliperContext.java      configuration et dépendances partagées
├── core/Vocabulary.java     contexte, types, actions, et ce que chaque type admet
├── model/
│   ├── Events.java          construction d'événements et d'enveloppes (pure, testée)
│   └── Envelope.java        vérification d'une enveloppe déposée (pure, testée)
├── sensor/
│   ├── AccessEventMapper    traduction des événements de l'ENT (pure, testée)
│   └── SensorService        lecture incrémentale et transmission périodique
├── security/                jetons d'accès OAuth (JDK uniquement)
├── service/                 PostgreSQL (schéma caliper)
└── controller/
    ├── TokenController      /caliper/oauth/token
    ├── EndpointController   /ims/caliper/v1p2 — dépôt et relecture
    └── AdminController      /caliper/admin — clients, état du capteur, volumétrie
```

## Scopes

**Caliper ne normalise aucun scope** : il décrit un endpoint protégé par un jeton porteur convenu
hors norme. Les nôtres sont donc les nôtres — `caliper.event.create` et `caliper.event.readonly`
— et portent un nom simple plutôt qu'une IRI sous `purl.imsglobal.org` : s'approprier l'espace de
noms de 1EdTech pour des scopes qu'il ne définit pas induirait en erreur le premier client qui
irait y chercher leur définition.

## Déploiement

Port **8174**, schéma SQL **`caliper`**, routes `/caliper` et `/ims/caliper`.

Le capteur reste au repos tant qu'aucune destination n'est configurée :

```json
"caliper": {
  "sensor": {
    "interval-seconds": 300,
    "endpoints": [
      { "url": "https://analytique.example.org/caliper", "token": "…", "batch-size": 500 }
    ]
  }
}
```

Le capteur lit `events.access_events`. Si l'ENT est configuré **sans magasin d'événements
PostgreSQL**, la lecture échoue proprement, l'état passe à `no-source` et le module continue de
servir son endpoint : il se tait plutôt que d'échouer à chaque tour.

## Tests

```bash
mvn test
```

33 tests sur les couches pures : vocabulaire et couples type/action, forme de l'enveloppe et des
événements, chaque motif de refus avec son rang, traduction des événements de l'ENT, comptage de
ce qui est écarté, et horodatage en temps universel.
