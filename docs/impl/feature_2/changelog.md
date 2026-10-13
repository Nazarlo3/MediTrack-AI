# Feature 2 — Журнал змін і рішень (Changelog)

Повний опис усіх покращень та рішень, внесених під час реалізації Feature 2 (Medication Tracker & Checkmarks).

---

## 📱 Мобільна частина (Android Client)
- **`AndroidManifest.xml`**:
  - Додано дозвіл `POST_NOTIFICATIONS` для надсилання сповіщень на Android 13+.
- **`SymptomModels.kt`**:
  - Додано дата-клас `MedicationItem` із підтримкою полів `photoUri`, `daysOfWeek`, `mealSlot` (година/їжа), `endDate`, `isTaken`.
- **`SymptomViewModel.kt`**:
  - Реалізовано збереження та завантаження списку ліків через `SharedPreferences` + Gson (`medications_list_v1`).
- **`MedicationSection.kt`**:
  - **Справжній вибір фото з галереї:** Інтегровано `ActivityResultContracts.GetContent()` для вибору реального фото ліків зі смартфона та показу мініатюри.
  - **Валідація дати:** Додано автоматичний розрахунок дати за замовчуванням (+7 днів від сьогодні) та перевірку, щоб користувач не міг обрати минулу дату.
  - **Точна година та сповіщення:** Вказівка точного часу прийому (наприклад, `08:00`) та автоматичний запит дозволу на системні сповіщення (`POST_NOTIFICATIONS`).
- **`SymptomCheckScreen.kt`**:
  - Додано перемикання між вкладками «Аналіз симптомів (AI)» та «Мої ліки».
