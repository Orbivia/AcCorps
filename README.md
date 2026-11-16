# AcCorps Android — prototype 0.4.1

Application Android native Java, Android 8+ : écoute externe en direct, gain manuel, filtres Cœur / Poumons / Brut, forme d’onde, enregistrements WAV PCM mono 48 kHz 16 bits, réécoute avec réglages et export par le sélecteur Android. Aucune permission Internet, fichiers privés, sauvegarde système désactivée.

## Utilisation
Brancher un casque filaire avec micro ou USB compatible. Autoriser le micro puis démarrer l’écoute. Commencer à faible volume. Enregistrer puis nommer la session à l’arrêt. Les WAV conservent le signal d’entrée avant les filtres de l’application. L’écoute s’arrête en quittant l’écran ou en cas de débranchement. La lecture utilise également les filtres et le gain choisis. Bluetooth exclu de cette première version. La sortie utilise un flux PCM stéréo 48 kHz : le menu Audio output permet Left only (par défaut, canal droit silencieux), Right only (canal gauche silencieux) ou Stereo (son dans les deux oreilles). Ce réglage prend effet pendant l’écoute et la relecture sans modifier les fichiers WAV mono originaux. Désactiver l’option d’accessibilité audio mono du téléphone pour conserver la séparation gauche/droite.

## Interface et réglages en direct
Interface en anglais par défaut, ressources centralisées dans `values/strings.xml`, palette PrintLeft claire/sombre selon le téléphone (fond gris, cartes de rayon 18 dp, jaune #FFCC32), onglets Listen / Recordings / Help. Bulle « i » accessible en en-tête : conditions d’utilisation, fonctionnement et confidentialité, sans validation forcée. Commandes Listen / Record groupées. Coupe-bas 10–500 Hz, coupe-haut 100–4000 Hz (écart minimum 20 Hz), réjecteur 50 Hz, activation/bypass et gain sont modifiables pendant l’écoute et la relecture. Les profils Cœur/Poumons chargent des points de départ ; une modification manuelle passe en réglage personnalisé. Les paramètres sont transmis ensemble au moteur au prochain bloc audio, avec transition lissée sur une constante de temps de 20 ms. Le réjecteur 50 Hz peut retirer du signal utile : il est désactivé par défaut. Les WAV restent indépendants des réglages.

## Traitement
AudioRecord UNPROCESSED → passe-haut/passe-bas du premier ordre (Cœur 25–400 Hz ; Poumons 80–2000 Hz ; Brut contourne les filtres) → réjecteur 50 Hz optionnel (Q=20) → gain −12 à +24 dB → montée progressive et saturation douce bornée → AudioTrack. Les profils sont exploratoires : aucune validation clinique ni mesure de fréquence cardiaque. Le profil Brut conserve la limitation de sortie. Aucun débruitage vocal qui pourrait supprimer des sons utiles. UNPROCESSED peut dépendre du téléphone ; le fichier ne garantit pas l’absence de traitement matériel. La limite numérique ne garantit pas un niveau acoustique sûr avec tous les écouteurs.

## Compilation
JDK 17, Android SDK 35, Gradle 8.11.1 : `gradle :app:assembleDebug :app:lintDebug`.
Le workflow GitHub conserve l’APK dans une release de test en brouillon privée (le nom de tag doit être incrémenté pour les prochaines versions). Il construit un APK de test signé avec la clé debug éphémère du runner (désinstaller l’ancienne build si signature différente). Pas de clé de production dans le dépôt.

## Limites et validation matérielle à réaliser
Latence réelle non mesurée ; le mode faible latence est une demande au système. Vérifier sur le téléphone de Pierre : sélection réelle du micro TRRS/USB, écoute simultanée, gain, débranchement, interruption/appels, saturation, fichiers et export, économie d’énergie. L’application n’enregistre pas en arrière-plan. Le conduit imprimé et la réponse fréquentielle du micro restent à caractériser. Les filtres ne recréent pas des fréquences absentes de la captation. Prototype, pas d’usage diagnostic validé.

## Références Android
https://developer.android.com/reference/android/media/AudioRecord
https://developer.android.com/reference/android/media/AudioTrack

## Essential 0.4.1
Scope en paysage (sensorLandscape), entête compact et historique audio glissant. Tous les blocs audio alimentent un tampon min/max à 1 ms : le rafraîchissement de l’écran ne supprime aucun bloc du tracé. Fenêtres 10 s / 5 s / 2,5 s pour les vitesses 0,5× / 1× / 2×, sans changer l’audio. Affichage d’amplitude à échelle automatique. « Pause scope » fige uniquement le tracé ; le son et l’enregistrement continuent. « Stop » termine la session audio.

Réglages centralisés dans l’engrenage : boutons de vitesse, profils, pas numériques ± et saisie directe, activation des filtres, réjecteur 50 Hz, gain et réinitialisation. Aucune SeekBar ni contrôle de plage. Réglages mémorisés sur le téléphone. Liste des WAV avec aperçus réels sous-échantillonnés, lecture, export, renommage et suppression avec confirmation. Nommage à la fin de l’enregistrement, avec conservation ou abandon confirmé.

Validation automatisée : DSP/WAV et ScopeHistory (continuité, chronologie après bouclage, conservation des extrema entre blocs, durée et fenêtres de vitesse), compilation et lint. La lisibilité effective sur appareil, la latence et les conditions acoustiques restent à tester sur le téléphone.
