---
name: meditrack-android
description: Розвивати Android-клієнт MediTrack AI та перевіряти його архітектуру, Compose-стан і збереження даних за рекомендаціями Google. Використовувати для змін Android-клієнта; не для Python-only задач, звітів лабораторних чи публікації релізу.
---

# Android у MediTrack

Це локальна адаптація, не офіційна skill Google. Рекомендації та перевірені розбіжності: [docs/android-guidelines.md](../../../docs/android-guidelines.md). Відносні шляхи нижче — від кореня репозиторію.

## Вибір потрібного контексту

- Вхід/DI: MediTrackApplication.kt, MainActivity.kt, di/SymptomDependencies.kt.
- Аналіз: ui/SymptomAnalysisController.kt → domain/symptoms/AnalyzeSymptomUseCase.kt → data/symptoms/DefaultSymptomRepository.kt.
- Ліки: ui/SymptomViewModel.kt → domain/medications/MedicationRepository.kt → data/medications/DefaultMedicationRepository.kt → LocalDataStore та ін'єктовані reminder/photo ефекти. di/MedicationDependencies.kt створює Android-адаптери; MedicationItem залишається старим DTO.
- Налаштування: ui/AppSettingsScreen.kt → SettingsViewModel → data/settings/SettingsRepository.kt; окремо data/DataCollectionSettings.kt. Вимкнення збору має діяти для наступних запитів і після перестворення Activity.

## Рішення під задачу

1. Визнач власника стану й джерело істини для зміненого сценарію. Екранний стан — у state holder/ViewModel; I/O та Android-сервіси — у data/adapters. Відокремлюй route зі збором стану від компонента state + callbacks, коли це спрощує конкретний екран.
2. Зберігай чинні контракти й міграції. Якщо виділяєш MedicationRepository/SettingsRepository, спочатку обгорни чинний LocalDataStore; не поєднуй це з переїздом у Room та редизайном.
3. Для Flow перевір lifecycle-aware collection і наявність сумісної залежності. Не підмінюй Android lifecycle Compose-пам'яттю remember. Для process death визнач мінімальний відновлюваний стан; не додавай приховане збереження текстів симптомів.
4. Використовуй поточну тему. Нові повторювані кнопки/картки/відступи узгоджуй між екранами; UI-тексти поступово винось у ресурси. Перевір поворот, Back, великий шрифт, світлу/темну тему лише для зміненого сценарію.
5. Нагадування: успішний notify перед підтвердженням доставки, обмежені повтори, стабільний ключ сповіщення. Поворот, відмова у дозволі й force-stop — різні сценарії; тестове сповіщення не доводить правильність графіка.
6. Напиши змістовну регресію на змінену поведінку, використовуючи fake/ін'єкцію clock/poster де доречно. Розрізняй JVM, instrumentation і live cloud. Не запускай повторний повний набір заради зміни документа.

## Офіційні додаткові skills

- [Compose performance](../jetpack-compose-performance/SKILL.md) — лише для performance-задачі; прочитай відповідні references перед висновком. Наявність Column або відсутність Baseline Profile сама по собі не доводить гальмування.
- [Permissions security](../android-permissions-security/SKILL.md) — дозволи, компоненти/IPC. Врахуй, чи компонент справді експортований. Встановлення skill не є дозволом змінювати permissions або налаштування телефона.

Версії API з upstream skill звіряй із Gradle проєкту й актуальними Android docs. Не встановлюй весь каталог, не піднімай Compose/SDK і не додавай Hilt/Room/Navigation без потреби поточної задачі. Ця skill не надає права commit/push/deploy чи розширювати погоджений рефакторинг.
