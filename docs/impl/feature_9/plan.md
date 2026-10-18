# Редагування ліків і кілька прийомів

Погоджено користувачем 17.10.2026.

Модель v3 з окремими часами й датою прийому для кожного часу. Сумісна міграція v1/v2 без вигаданих годин. Редагування зі стабільним ID й без втрати фото. Окремі alarm/сповіщення за препарат+час, скасування застарілого графіка. Дії «Прийняв» і «Через 10 хв» перевіряють ID, час, дату й версію графіка. Відкладення переживає перезапуск і не змінює інші прийоми.

Порядок: модель/сумісність → логіка → UI → змістовні тести → одна фінальна збірка/lint → оновлення KB. Попередні записи не видаляти. Нових бібліотек не додавати. Комітів, push і deploy без окремої вказівки не виконувати.

## Файли

Основні файли: network/SymptomModels.kt; data/MedicationJsonCodec.kt, LocalDataStore.kt; validation/FormValidators.kt; reminders/MedicationSchedule.kt, MedicationReminderScheduler.kt, MedicationReminderReceiver.kt, MedicationNotificationActionReceiver.kt, DoseActionPolicy.kt; ui/MedicationSection.kt, SymptomViewModel.kt, SymptomCheckScreen.kt; AndroidManifest.xml; MultiDoseTest, MedicationUpgradeInstrumentedTest, MedicationFormInstrumentedTest. README і KB — спільне оновлення.
