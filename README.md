# AcCorps Android — prototype 0.1.0

Application Android native Java, Android 8+ : écoute externe en direct, gain manuel, filtres Cœur / Poumons / Brut, forme d’onde, enregistrements WAV PCM mono 48 kHz 16 bits, réécoute avec réglages et export par le sélecteur Android. Aucune permission Internet, fichiers privés, sauvegarde système désactivée.

## Utilisation
Brancher un casque filaire avec micro ou USB compatible. Autoriser le micro puis démarrer l’écoute. Commencer à faible volume. Enregistrer puis nommer la session à l’arrêt. Les WAV conservent le signal d’entrée avant les filtres de l’application. L’écoute s’arrête en quittant l’écran ou en cas de débranchement. La lecture utilise également les filtres et le gain choisis. Bluetooth exclu de cette première version.

## Traitement
AudioRecord UNPROCESSED → passe-haut/passe-bas du premier ordre (Cœur 25–400 Hz ; Poumons 80–2000 Hz ; Brut contourne les filtres) → gain −12 à +24 dB → montée progressive et saturation douce bornée → AudioTrack. Les profils sont exploratoires : aucune validation clinique ni mesure de fréquence cardiaque. Le profil Brut conserve la limitation de sortie. Aucun débruitage vocal qui pourrait supprimer des sons utiles. UNPROCESSED peut dépendre du téléphone ; le fichier ne garantit pas l’absence de traitement matériel. La limite numérique ne garantit pas un niveau acoustique sûr avec tous les écouteurs.

## Compilation
JDK 17, Android SDK 35, Gradle 8.11.1 : `gradle :app:assembleDebug :app:lintDebug`.
Le workflow GitHub conserve l’APK dans une release de test en brouillon privée (le nom de tag doit être incrémenté pour les prochaines versions). Il construit un APK de test signé avec la clé debug éphémère du runner (désinstaller l’ancienne build si signature différente). Pas de clé de production dans le dépôt.

## Limites et validation matérielle à réaliser
Latence réelle non mesurée ; le mode faible latence est une demande au système. Vérifier sur le téléphone de Pierre : sélection réelle du micro TRRS/USB, écoute simultanée, gain, débranchement, interruption/appels, saturation, fichiers et export, économie d’énergie. L’application n’enregistre pas en arrière-plan. Le conduit imprimé et la réponse fréquentielle du micro restent à caractériser. Les filtres ne recréent pas des fréquences absentes de la captation. Prototype, pas d’usage diagnostic validé.

## Références Android
https://developer.android.com/reference/android/media/AudioRecord
https://developer.android.com/reference/android/media/AudioTrack
