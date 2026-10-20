# ЛР6: відтворюване порівняння AI

**Поточне оцінювання — [details](details/README.md), 20.10.2026:** нові тривалість/інтенсивність проти вихідної 0.3. Runner тепер типово використовує `--suite details`, паузу 15 с. Нижче — опис попереднього набору **legacy** від 18.10 (7/16 категорій); його знімки й результати збережені окремо.

Тільки синтетичні дані. Це технічна перевірка категоризації/контексту, не клінічна оцінка. [План і пороги](../../docs/ai-development.md) записані до запуску.

Із кореня репозиторію, у Python-середовищі backend:

```powershell
python -m pip install -r backend/requirements-dev.txt
python backend/evaluation/run.py --suite legacy
python -m unittest discover -s backend/tests -v
# Лише з власним серверним GROQ_API_KEY у backend/.env або середовищі:
python backend/evaluation/run.py --suite legacy --live
```

Без `--live` мережі немає. `--live` робить **20** зовнішніх запитів: 10 однакових вводів для кожної версії, без повторів. Результат — новий JSON у results; `--output` дозволяє інший новий шлях, наявний файл не перезаписується. Транспортні помилки, 429 і невірні відповіді зберігаються як невдалі спроби. Код виходу 1 означає невиконання порога current; невдалий baseline сам по собі не робить прогін неуспішним. Повторний запуск — окремий вимір, не заміна попереднього.

Поточна типова пауза 15 с зменшує ризик token-rate limit, але не гарантує доступності. Явне `--suite legacy --live --resume backend/evaluation/results/<попередній>.json` повторює лише рядки без валідної відповіді в новий файл. Попередній JSON, початковий summary і всі спроби зберігаються. Валідні відповіді з невідповідною категорією не повторюються. Приймання після такого продовження не дорівнює успіху з першої спроби. Історичний повтор 18.10 мав паузу 12 с; цей факт у протоколах не змінюється.

- `baseline.json` — точний старий промпт з Git, 7 категорій, без context, max_tokens=200.
- `current.json` — точний промпт на початку ЛР6, 16 категорій, context, max_tokens=400.
- `cases.json` — 5 регресій + 3 нових категорії/сценарії + 2 контекстні перевірки. Це новий набір, не дослівні вводи ЛР4.
- `run.py` — реальні AnalysisService/GroqProvider та той самий HTTP-транспорт; baseline підміняє лише messages/max_tokens/набір категорій. Валідація результату сучасна для обох: старий permissive-парсер навмисно не виконується. Повні старий сервер/APK не запускаються.

Параметри моделі беруться з config.py (`GROQ_MODEL`, типово qwen/qwen3.8-27b), timeout також звідти. Результат записує фактичну модель/параметри/версії пакетів/хеші. Python-залежності runtime поки не закріплені, тому ці метадані описують виміряне середовище, але не є lockfile. Провайдер може змінити поведінку моделі; ідентичність повторних відповідей не гарантується. Fake не використовується у живому порівнянні.

У artifacts немає заголовків, API-ключів або вмісту .env. Не додавайте персональні описи в cases. Production URL Android і сервер Render цей інструмент не змінює.

## Один мобільний сценарій

Потрібні сумісний встановлений debug-застосунок, test APK із `:app:assembleDebugAndroidTest`, один авторизований ADB-пристрій і локальний backend на 127.0.0.1:18765 з AI_PROVIDER=groq. Встановлюйте test APK через `adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`; це не оновлює основний застосунок.

```powershell
adb reverse tcp:18765 tcp:18765
adb shell am instrument -w -r -e class com.example.meditrackai.Lab5PhoneScenarioTest#lab6InjuryClarificationChangesCategoryWithoutRewritingHistory -e lab5Phone true -e lab5BaseUrl http://127.0.0.1:18765/ com.example.meditrackai.test/androidx.test.runner.AndroidJUnitRunner
adb reverse --remove tcp:18765
```

Назви lab5Phone/lab5BaseUrl залишено, бо сценарій повторно використовує наявне ізольоване оточення ЛР5; banner показує ЛР6. Це два живі AI-виклики. SharedPreferences мають унікальний тестовий префікс і прибираються у finally. PNG зберігаються в cache/lab5-phone/04_lab6_initial.png та 05_lab6_clarified.png всередині debug-пакета. Після перепідключення ADB reverse треба відновити. Протоколи виконаних запусків — [mobile.json](results/mobile.json); нові фото й Word-звіт лишаються локальними у docs/various files.
