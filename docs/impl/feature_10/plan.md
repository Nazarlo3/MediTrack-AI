# Уточнення AI в межах перевірки

Погоджено користувачем 17.10.2026.

Зворотно сумісне необов’язкове поле context з обмеженням довжини й кількості повідомлень. Android показує попередню відповідь і поле уточнення. Нова відповідь додається окремим записом з усім контекстом, старі записи не переписуються. Повтор після помилки використовує той самий запит. Документувати потребу розгорнути backend перед використанням уточнення.

Порядок: модель/сумісність → логіка → UI → змістовні тести → одна фінальна збірка/lint → оновлення KB. Попередні записи не видаляти. Нових бібліотек не додавати. Комітів, push і deploy без окремої вказівки не виконувати.

## Файли

Основні файли: backend/models.py, main.py, ai_service.py, tests/test_ai_service.py; network/SymptomModels.kt; validation/SymptomResponseValidator.kt; ui/SymptomViewModel.kt, SymptomCheckScreen.kt, ClarificationSection.kt, HistorySection.kt; docs/api-contract.md, ai-component.md, testing.md, known-limitations.md і README.
