# Actifit Android Version
Actifit: It Pays to be Fit!


Actifit tracks and rewards your activity with Actifit's AFIT tokens, but also HIVE, SPORTS and other token rewards.

##### Earn Tokens As Simple As One Two Three
1. Download the Actifit mobile app [Google Play](https://links.actifit.io/android) | [App Store](https://links.actifit.io/ios)
2. Go for a jog, walk your dog, maw your lawn, go to the gym, move around your office,... with an aim to reach a minimum of 5,000 activity count.
3. Post via app, and get rewarded!


##### Actifit (AFIT) Tokens Use Cases
AFIT tokens can be exchanged on Actifit Market to signup for fitness or nutrition related consultation sessions, buying ebooks, boosting your rewards via purchasing actifit based booster gadgets, or earning extra HIVE rewards!

You can buy AFIT tokens on [Hive-Engine.com](https://hive-engine.com/?p=market&t=AFIT)

##### Delegate To Earn More Rewards
You can earn more AFIT tokens if you are a HIVE token holder. Delegate Hive Power to Actifit and earn your share of ~14,000 AFIT tokens distributed per day to our delegators, as well as a weekly share of the 5% beneficiary reward of actifit posts

For a more detailed briefing on the project, check out our introductory post: Announcing Actifit: innovative SMT for rewarding fitness activity!
https://actifit.io/actifit/@actifit/announcing-actifit-innovative-smt-for-rewarding-fitness-activity

##### Sign Up For a New Account
[Signup Link](https://actifit.io/signup)
In order to use Actifit, you need an account on the Hive blockchain (as well as an optional account on Blurt blockchain). If you do not have an account, you can sign up for one right now!
You can create your actifit account for as low as 2$, and get following extra benefits:
- Your actifit account, usable across the Hive and Blurt blockchains and all cool relevant dapps available therein.
- A minimum of 100 AFIT tokens as a free reward. The higher you invest (in batches of 5$), the higher the amount rewarded.
- The Hive blockchain require a min amount of RC (which controls how often you can transact). To help with that, we delegate to your new account a minimum value RC to allow you to properly transact and post easily once per day!
- Via posting your daily activity, you are eligible to earn AFIT tokens, HIVE and BLURT upvotes, as well as SPORTS and other tokens, a free source of earning crypto while getting healthy and fit!
- Owning AFIT tokens allows you to earn more rewards for your daily activity, as it increases your User Rank. 
[Signup Link](https://actifit.io/signup)


## For Developers — Required Local Files

The following files are **git-ignored** (they hold machine-specific paths and secrets) but are required to build the project. For each one, copy the matching `.example` template to the real filename and replace every `*****` placeholder with the real value. Secrets are provided separately by the project owner through a secure channel — never commit them.

| Real file (create this) | Template | Required for | Notes |
|---|---|---|---|
| `local.properties` | [`local.properties.example`](local.properties.example) | All builds | Set `sdk.dir` to your own Android SDK path (Android Studio auto-creates this) and fill in `gemini.api.key` (injected into `BuildConfig.GEMINI_API_KEY`). |
| `keystore.properties` | [`keystore.properties.example`](keystore.properties.example) | All builds | Loaded unconditionally by `app/build.gradle`, so it must exist even for debug. Fill in the alias and passwords. |
| `app/google-services.json` | [`app/google-services.json.example`](app/google-services.json.example) | All builds | Firebase config — the `google-services` Gradle plugin fails without it. Get the real file from the Firebase console (project `actifit-io`) or the project owner. |
| `app/src/main/res/values/unofficial_strings.xml` | [`app/src/main/res/values/unofficial_strings.xml.example`](app/src/main/res/values/unofficial_strings.xml.example) | All builds | App endpoints + credentials (Fitbit, DeepL, AdMob, media, sign key, etc.). Won't compile without it; masked keys must be filled in for the related features to work. |
| `keystore/*.jks` (+ `pepk.jar`, `encryption_public_key.pem`) | — (binary, not templatable) | **Release** builds only | Signing keystores referenced by `keystore.properties` → `storeFile`. Obtain from the project owner over a secure channel. Debug builds (`gradlew.bat assembleDebug`) work without them. |

After placing the files, verify the setup:

```bash
gradlew.bat assembleDebug      # Windows
./gradlew assembleDebug        # macOS/Linux
```

> ⚠️ **Security:** These files stay in `.gitignore` for a reason. Share real secrets only through a secure channel, never via the repo or plain email.

##### Contact us on
[Our Website](https://actifit.io) |
[Our blog](https://actifit.io/actifit/blog) |
[Discord](https://links.actifit.io/discord) |
[Facebook](https://www.facebook.com/Actifit.fitness/) |
[Twitter](https://www.twitter.com/Actifit_fitness) |
[Instagram](https://www.instagram.com/actifit.fitness/) |
[Download on Google Play](https://links.actifit.io/android) | [Download on App Store](https://links.actifit.io/ios)

