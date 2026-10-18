# Стан і наступні кроки MediTrack AI

Стан робочого дерева на 17.10.2026. «Реалізовано» означає наявність у коді, а не перевірений deploy або проходження тестів сьогодні. Карта джерел і команди — [architecture.md](architecture.md), компроміси — [decisions.md](decisions.md).

## Реалізовано

| Можливість | Доказ |
|---|---|
| Аналіз симптомів, 16 категорій, три urgency, ручний повтор помилки | [SymptomAnalysisController](../app/src/main/java/com/example/meditrackai/ui/SymptomAnalysisController.kt), [ai_service.py](../backend/ai_service.py) |
| До трьох AI-уточнень, питання, передача контексту та перевірка context_used | [models.py](../backend/models.py), [ClarificationSection](../app/src/main/java/com/example/meditrackai/ui/ClarificationSection.kt), [SymptomModels](../app/src/main/java/com/example/meditrackai/network/SymptomModels.kt) |
| Постійна історія до 20 результатів, деталі, міграція, видалення одного/всіх з підтвердженням | [LocalDataStore](../app/src/main/java/com/example/meditrackai/data/LocalDataStore.kt), [HistoryJsonCodec](../app/src/main/java/com/example/meditrackai/data/HistoryJsonCodec.kt), [HistorySection](../app/src/main/java/com/example/meditrackai/ui/HistorySection.kt) |
| Додавання/редагування/видалення ліків, локальні фото, до 12 часів, окремі статуси за датою, зелене підсвічення | [MedicationSection](../app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt), [MedicationJsonCodec](../app/src/main/java/com/example/meditrackai/data/MedicationJsonCodec.kt) |
| Локальні alarm, дні/кінець курсу, дозволи, відновлення за системними подіями, «Прийняв»/«Через 10 хв» | [reminders/](../app/src/main/java/com/example/meditrackai/reminders/) |
| Початкова вкладка ліків, системна/світла/темна тема та її збереження | [MainActivity](../app/src/main/java/com/example/meditrackai/MainActivity.kt), [ThemeSettings](../app/src/main/java/com/example/meditrackai/data/ThemeSettings.kt) |

## Реалізовано з межами або невідомим зовнішнім станом

- AI-уточнення реалізовані з обох боків, але поточну версію Render не звіряли. Старий сервер може прийняти запит без використання context; клієнт відхиляє таке уточнення. [README](../README.md) відокремлює поточні зміни від APK 0.2; Підготовлена версія 0.3 має Gradle versionCode/versionName 3/0.3; APK 0.2 залишається попереднім опублікованим релізом.
- Відкладення не перетинає локальну північ. Відновлення alarm є в коді, але не гарантує доставку після force stop чи обмежень виробника. Запис delivered перед notify залишає можливість пропуску при аварії.
- Зберігаються результати й форми, але lastRequest/AI-контекст існують лише в controller, що належить ViewModel; після завершення процесу діалог не відновлюється. [SymptomViewModel](../app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt).
- Захист від пошкодженого JSON блокує перезапис, але автоматичного відновлення/екрана ремонту немає. Один readError блокує обидва набори даних і синхронізацію нагадувань. [LocalDataStore](../app/src/main/java/com/example/meditrackai/data/LocalDataStore.kt).
- Категоризація й urgency залежать від LLM. Є правила промпту, форматна перевірка та застереження; незалежного механізму оцінки невідкладних станів і доказів клінічної точності немає.

## Підтверджено відсутні можливості

- Офлайн-черга зі статусом «очікує» та ручним надсиланням після відновлення зв’язку — не реалізований підвищений рівень ЛР4, не обов’язок базового рівня. [HistoryJsonCodec](../app/src/main/java/com/example/meditrackai/data/HistoryJsonCodec.kt) приймає лише «готово».
- Тривалість симптому в днях є лише в [experiment/](../experiment/), її немає в чинних моделях API.
- Адреса сервера/API-ключ у налаштуваннях, експорт/імпорт, облікові записи й синхронізація не реалізовані. Це не автоматично погоджений обсяг майбутньої роботи.

## Підтверджені проблеми

1. **Різна валідація довжини.** Android перевіряє description після trim; backend — до strip, а потім лише порожнечу. Короткий текст, доповнений пробілами до трьох символів, може пройти backend. Доказ: [FormValidators.symptomError](../app/src/main/java/com/example/meditrackai/validation/FormValidators.kt), [SymptomRequest](../backend/models.py), [analyze_symptom](../backend/main.py).
2. **Документація містить застарілі твердження.** [requirements.md](requirements.md) називає кілька часів/відкладення незавершеними, [overview.md](overview.md) — сім категорій; код уже має відповідно v3/дії та 16 категорій. Старі фрагменти [api-contract.md](api-contract.md) описують чотири поля, пізніше доповнення — шість. Ці сторонні документи за умовою цього завдання не редагувалися.
3. **Відтворюваність і захист не завершені.** [requirements.txt](../backend/requirements.txt) не закріплює версій; явних backup-виключень немає, cleartext дозволено; backend не містить auth/rate limit. Це факти конфігурації, а не доказ експлуатації вразливості. Причини частини налаштувань невідомі.

## Рекомендації за пріоритетом

Це пропозиції за результатами огляду, а не затверджений користувачем план.

| Пріоритет | Наступний крок |
|---|---|
| P1 | Узгодити валідацію нормалізованого опису та додати граничний тест; не змінювати API без явного рішення. |
| P1 | Перед використанням уточнень звірити серверний контракт і зробити один наскрізний синтетичний запит із контекстом. Deploy потребує окремої вказівки. |
| P1 | Визначити політику backup/перенесення чутливих даних, cleartext і захисту публічного backend; не змінювати їх непомітно. |
| P2 | Підтвердити на цільовому телефоні reboot, наступний день, часовий пояс, відмову в дозволах та реальний відкладений сигнал. Наявний код і unit-тести не замінюють доставку Android. |
| P2 | Перевірити нове скасування/чернетки на телефоні та завершення процесу. Retry/стани/cancel controller вже перевіряються JVM-тестами; backend має HTTP TestClient. Повний mobile → backend → Groq сценарій після рефакторингу не перевірено. |
| P2 | Окремою задачею закріпити Python-залежності, описати CI та узгодити інші KB-документи; визначити версію наступного релізу. |
| P3 | За потреби поширити на ліки вже наявний для аналізу repository/use case/DI, ізолювати помилки сховищ та відновлювати контекст. Перехід на Room сам по собі не є вимогою. |

Початковий огляд був статичним. Нові перевірки ЛР5 наведені нижче й у testing.md; хмарний deploy та телефон не перевірялися.

## ЛР5 — оновлення 17.10.2026

Виконано варіант Б: незалежні domain-моделі та AIProvider, GroqProvider/FakeProvider, AnalysisService, Depends, централізовані Settings і domain → HTTP error handlers. Після рефакторингу додано request_id у X-Request-ID і логах. README та KB оновлено; [журнал](impl/feature_12/changelog.md).

29 автоматизованих backend-тестів пройшли; HTTP-контракт перевірено TestClient із fake та mock HTTP реального адаптера. Окремо один ручний синтетичний виклик FastAPI → Groq завершився 200 зі сумісним JSON. Це не deploy і не перевірка телефона проти оновленого Render.

Залишається: за окремою вказівкою розгорнути backend і перевірити Android → Render → Groq; за потреби підготувати звіт із двома локальними demo-знімками. Валідація trim, політика backup/auth/rate limit, фіксація runtime-залежностей і CI залишаються відкритими. Встановлена Starlette виводить попередження про майбутню відмову від HTTPX у TestClient; тести проходять, перехід тестового клієнта не робився.

## ЛР5 — додатково виконано варіант А Android

Аналіз/уточнення/історія тепер мають незалежні domain-моделі, SymptomRepository (real і test fake), mapper, AnalyzeSymptomUseCase, типізовані помилки й ручну constructor DI. ViewModel делегує цей сценарій controller у viewModelScope. Після checkpoint додано «Скасувати аналіз» із захистом від запізнілих відповідей; початковий текст і чернетка уточнення зберігаються.

Перевірено локально: 57 unit-тестів без помилок/пропусків, lint 0 помилок/50 попереджень, assembleDebug та compileDebugAndroidTestKotlin успішні. Пізніше виконано [feature_14](impl/feature_14/changelog.md): debug APK встановлено без очищення даних; три UI-сценарії на фізичному Android 15 з локальним FastAPI → справжнім Groq успішні. Перевірено аналіз/уточнення, повтор, історію й cancel/чернетки. Поворот/завершення процесу та Render залишаються неперевіреними. Це межа перевірки, а не відсутність реалізації. [Журнал](impl/feature_13/changelog.md).
