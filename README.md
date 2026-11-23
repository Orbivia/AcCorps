# AcCorps 0.7.1 — Pause audio

Pause coupe la sortie audio et suspend le tracé, le compteur et les données ajoutées au WAV. Reprendre poursuit la même session. En direct, les données micro reçues pendant la pause sont écartées ; en réécoute, la position du fichier est conservée (hors bloc déjà en cours). Stop termine également une session en pause.

# AcCorps Android — prototype 0.7.0

Application Android native Java, Android 8+ : écoute externe en direct, gain manuel, filtres Cœur / Poumons / Brut, forme d’onde, enregistrements WAV PCM mono 48 kHz 16 bits, réécoute avec réglages et export par le sélecteur Android. Aucune permission Internet, fichiers privés, sauvegarde système désactivée.


## 0.7.0 — Scope priority, quick modes, experimental pitch shift
- Portrait display: slightly smaller header and controls, 6-second default oscilloscope, higher-contrast yellow waveform/envelope, tap toggles 1× / 2× zoom. Original scope history remains 1 ms min/max + RMS.
- Quick Heart / Lungs buttons apply the existing 20–600 Hz and 80–3000 Hz exploratory filter presets. Custom opens the existing filters panel. Existing Heart low, Heart full, Raw and Focused choices are retained.
- Dialog display is fitted to each tab; Audio contains output routing, pitch controls and gain, Filters contains high-pass/low-pass, presets, notch and character.
- Experimental real-time transposition, factor ×1.0–×5.0, 0–100% shifted mix, no change to recorded timebase. Two overlapping grains are used; artifacts and ~100 ms of initial processing history are possible, and real-device latency is not verified. Pitch is **off by default**.
- A/B bypasses frequency filters and pitch transposition for monitoring (existing gain and limiter still apply). The scope is drawn from pre-transposition processed audio to preserve visual clarity.
- **Original 48 kHz mono PCM WAV is still saved before DSP**, and only monitoring/playback applies the experimental transposition. No clinical validation. Listen at low volume, especially when changing gain.
- Java tests: pitch-up responses to 40, 60 and 100 Hz synthetic input, plus legacy DSP and scope history tests. Android Gradle assembleDebug and lintDebug are performed in CI. Test builds are disposable debug signatures.

## 0.6.0 — Scope, ergonomie et comparaison audio
- Affichage à échelle verticale progressive : la référence suit immédiatement les pics puis décroît progressivement pour limiter les changements brusques de taille. La graduation reste relative, non calibrée.
- Tap sur le scope : alternance entre 1× et 4× (fenêtre de 5 s ou 1,25 s), sans modification des échantillons ni de la vitesse audio. Réglages supplémentaires 0,5× et 2×.
- Réglages séparés en trois vues **Display / Audio / Filters** pour rester utilisables sur téléphone en portrait.
- Comparaison A/B immédiate (filtré ↔ brut), temporaire et non enregistrée. Elle contourne les filtres fréquentiels mais conserve le gain et le limiteur ; surveiller le volume.
- Indicateur d'amplitude d'entrée en dBFS et indication CLIP ou LIMIT fondées sur les pics des blocs audio (ces niveaux ne constituent pas un sonomètre calibré).
- Presets exploratoires Heart low 20–160 Hz, Heart full 20–600 Hz, Lungs 80–3000 Hz, Raw et Custom. Ils ne prétendent pas représenter une validation clinique.
- Icône AcCorps dans l'en-tête, bouton d'enregistrement rouge pendant la capture, date des sessions dans les cartes, écran vide plus explicite.
- Conditions d'utilisation réparties en sections dépliables ; affichage dynamique de la version installée.
- Les WAV mono 48 kHz PCM 16 bits restent strictement enregistrés **avant** les filtres, le gain et la comparaison A/B. Le traitement s'applique à l'écoute et lors de la lecture.
- Validation : tests Java DSP/WAV et ScopeHistory, assembleDebug et lintDebug exécutés par GitHub Actions. La qualité acoustique, la latence, les routes USB/TRRS et l'ergonomie tactile restent à vérifier sur un appareil réel.

## Utilisation
Brancher un casque filaire avec micro ou USB compatible. Autoriser le micro puis démarrer l’écoute. Commencer à faible volume. Enregistrer puis nommer la session à l’arrêt. Les WAV conservent le signal d’entrée avant les filtres de l’application. L’écoute s’arrête en quittant l’écran ou en cas de débranchement. La lecture utilise également les filtres et le gain choisis. Bluetooth exclu de cette première version. La sortie utilise un flux PCM stéréo 48 kHz : le menu Audio output permet Left only (par défaut, canal droit silencieux), Right only (canal gauche silencieux) ou Stereo (son dans les deux oreilles). Ce réglage prend effet pendant l’écoute et la relecture sans modifier les fichiers WAV mono originaux. Désactiver l’option d’accessibilité audio mono du téléphone pour conserver la séparation gauche/droite.

## Interface et réglages en direct
Interface en anglais par défaut, ressources centralisées dans `values/strings.xml`, palette PrintLeft claire/sombre selon le téléphone (fond gris, cartes de rayon 18 dp, jaune #FFCC32), onglets Listen / Recordings / Help. Bulle « i » accessible en en-tête : conditions d’utilisation, fonctionnement et confidentialité, sans validation forcée. Commandes Listen / Record groupées. Coupe-bas 10–500 Hz, coupe-haut 100–4000 Hz (écart minimum 20 Hz), réjecteur 50 Hz, activation/bypass et gain sont modifiables pendant l’écoute et la relecture. Les profils Cœur/Poumons chargent des points de départ ; une modification manuelle passe en réglage personnalisé. Les paramètres sont transmis ensemble au moteur au prochain bloc audio, avec transition lissée sur une constante de temps de 20 ms. Le réjecteur 50 Hz peut retirer du signal utile : il est désactivé par défaut. Les WAV restent indépendants des réglages.

## Traitement
AudioRecord UNPROCESSED → passe-haut/passe-bas Butterworth du deuxième ordre (Cœur 20–400 Hz ; Poumons 100–2000 Hz ; Brut contourne les filtres) → réjecteur 50 Hz optionnel (Q=20) → gain −12 à +24 dB → montée progressive et saturation douce bornée → AudioTrack. Les profils sont exploratoires : aucune validation clinique ni mesure de fréquence cardiaque. Le profil Brut conserve la limitation de sortie. Aucun débruitage vocal qui pourrait supprimer des sons utiles. UNPROCESSED peut dépendre du téléphone ; le fichier ne garantit pas l’absence de traitement matériel. La limite numérique ne garantit pas un niveau acoustique sûr avec tous les écouteurs.

## Compilation
JDK 17, Android SDK 35, Gradle 8.11.1 : `gradle :app:assembleDebug :app:lintDebug`.
Le workflow GitHub publie l'APK de test comme artefact téléchargeable de l'exécution GitHub Actions ; l'ancienne release de test peut pointer sur un build précédent et ne doit pas être utilisée pour cette version. Il construit un APK de test signé avec la clé debug éphémère du runner (désinstaller l’ancienne build si signature différente). Pas de clé de production dans le dépôt.

## Limites et validation matérielle à réaliser
Latence réelle non mesurée ; le mode faible latence est une demande au système. Vérifier sur le téléphone de Pierre : sélection réelle du micro TRRS/USB, écoute simultanée, gain, débranchement, interruption/appels, saturation, fichiers et export, économie d’énergie. L’application n’enregistre pas en arrière-plan. Le conduit imprimé et la réponse fréquentielle du micro restent à caractériser. Les filtres ne recréent pas des fréquences absentes de la captation. Prototype, pas d’usage diagnostic validé.

## Références Android
https://developer.android.com/reference/android/media/AudioRecord
https://developer.android.com/reference/android/media/AudioTrack

## Essential 0.5.0
Scope en paysage (sensorLandscape), entête compact et historique audio glissant. Tous les blocs audio alimentent un tampon min/max à 1 ms : le rafraîchissement de l’écran ne supprime aucun bloc du tracé. Fenêtres 10 s / 5 s / 2,5 s pour les vitesses 0,5× / 1× / 2×, sans changer l’audio. Affichage d’amplitude à échelle automatique. Depuis 0.7.1, « Pause » suspend aussi le son et l’enregistrement. « Stop » termine la session audio.

Réglages centralisés dans l’engrenage : boutons de vitesse, profils, pas numériques ± et saisie directe, activation des filtres, réjecteur 50 Hz, gain et réinitialisation. Aucune SeekBar ni contrôle de plage. Réglages mémorisés sur le téléphone. Liste des WAV avec aperçus réels sous-échantillonnés, lecture, export, renommage et suppression avec confirmation. Nommage à la fin de l’enregistrement, avec conservation ou abandon confirmé.

Validation automatisée : DSP/WAV et ScopeHistory (continuité, chronologie après bouclage, conservation des extrema entre blocs, durée et fenêtres de vitesse), compilation et lint. La lisibilité effective sur appareil, la latence et les conditions acoustiques restent à tester sur le téléphone.

## Prototype 0.5.0 — écoute et affichage
Icône provisoire vectorielle jaune en spirale sur fond ardoise (adaptive icon), facilement remplaçable.
Profils Cœur 20–400 Hz et Poumons 100–2000 Hz avec filtres passe-haut et passe-bas Butterworth du deuxième ordre (12 dB/octave chacun), paramètres lissés en direct. Gain manuel et limitation numérique douce ; ne pas activer le notch 50 Hz sans vérifier qu'il ne supprime pas de composantes cardiaques utiles.
Affichage Envelope (par défaut, RMS par milliseconde avec attaque/relâchement) ou Waveform (oscillogramme filtré), tous deux à échelle relative automatique. Le tracé reste audio et non ECG. WAV mono conservé avant filtres et gain. Validation acoustique sur microphone, pavillon, câble et casque encore nécessaire ; application expérimentale sans usage diagnostic validé.

## Lecture WAV sans microphone — 0.5.3
En mode Play, aucun microphone n'est nécessaire : la sortie utilise le routage média Android (haut-parleur du téléphone ou casque connecté), et un débranchement de périphérique externe ne stoppe plus automatiquement la lecture. La sortie du haut-parleur intégré utilise les deux canaux, tandis que les casques respectent le choix Left only/Right only/Stereo. En mode Listen, le microphone externe et une sortie filaire restent exigés afin de limiter les risques de Larsen. Les WAV originaux ne changent pas.

## Interface portrait — 0.5.4
Le mode portrait remplace le mode paysage imposé. Le scope reste la zone centrale et défile horizontalement. L'en-tête donne la priorité au titre et aux accès information/réglages, avec le statut sur sa propre ligne. Les commandes Listen, Record et Stop restent ensemble au bas du scope et la durée/Recordings occupent une seconde ligne. Les sessions enregistrées s'affichent en cartes verticales : titre, durée et aperçu au-dessus de Play/Export/More. Les réglages restent dans l'engrenage avec défilement vertical, sans sliders. Aucun changement de traitement audio ou des fichiers existants.

## Icône AcCorps — 0.5.5
Le lanceur utilise le logo spiralé/onde `AcCorps_logo.png` (fichier PNG identique à l'identité graphique, copié dans `app/src/main/res/drawable-nodpi/`). Icône adaptative sur fond blanc, image centrée avec marge de sécurité. Aucun changement au moteur audio, aux filtres ou au stockage des WAV.
