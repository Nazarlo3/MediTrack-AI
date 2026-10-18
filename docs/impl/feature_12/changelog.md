# ЛР5 — Журнал змін

## 17.10.2026

- Погоджено варіант Б і сценарій аналізу симптомів; записано план та межі змін.
- Початкове робоче дерево містить сторонні незакомічені зміни; вони зберігаються. Комітів і розгортання немає.
- Додано domain dataclass/AIProvider Protocol і domain errors без FastAPI/Pydantic/requests; AnalysisService отримує провайдер у конструкторі. HTTP-схеми мають явні to_domain/from_domain.
- GroqProvider зберіг попередні промпт, категорії та формат; FakeProvider надає явно синтетичний успіх або контрольовану помилку. Підключення — FastAPI Depends з composition root у dependencies.py.
- config.py став єдиним читачем середовища/.env; provider/model/timeout налаштовуються централізовано, ключ приховано з repr. Значення справжнього ключа та .env не змінювалися. Fake не використовується як автоматичний fallback.
- У main.py залишені HTTP-маршрути й централізовані handlers; шість JSON-полів, HTTP 400/422/503/500 та попередня семантика довжини збережені.
- Після перевірки рефакторингу додано контрольне розширення: серверний request_id у X-Request-ID та логах, включно з 500. Клієнтський ID не віддзеркалюється; описи, ключі й сирі exception не логуються цими компонентами.
- Оновлено README, architecture, decisions, ROADMAP, api-contract, ai-component, testing та known-limitations. Android не змінювався в цій задачі.

## Фактичні перевірки

1. Baseline: 15 backend-тестів — OK, 1.277 с.
2. Після domain/service/provider/DI, до request_id: 21 тест — OK, 0.113 с.
3. Фінальний набір: 29 тестів (15 regression + 6 service + 8 HTTP) — OK, 0.151 с, без помилок і пропусків. Unit/HTTP-тести використовують fake/mock, не зовнішній AI.
4. Один окремий ручний синтетичний виклик локальний FastAPI TestClient → справжній Groq: HTTP 200, очікувані JSON-поля/категорія/urgency та X-Request-ID коректні. Це не перевірка Android → Render → Groq і не deploy.
5. Два локальні PNG демонстрації фактичних HTTP 200/503 із FakeProvider переглянуто. Це браузерні знімки сторінок зі збереженими TestClient-відповідями та відповідними ID у логах, не скриншоти телефона/Swagger чи живого AI. Каталог: docs/various files/screenshot/ЛР5; він ігнорується Git.

HTTPX 0.28.1 / httpcore 1.0.9 встановлені лише в ізольоване .artifacts/lab5-venv із system-site-packages. Новий requirements-dev.txt потрібний для TestClient; runtime requirements.txt не змінено. Starlette повідомляє deprecation warning про HTTPX, але всі тести проходять; зміну тестового клієнта не робили.

Android-збірки й тести, commit, push та cloud deploy не виконувалися. ЛР5-код і локальна демонстрація готові; перевірка телефона проти оновленого сервера можлива після окремо погодженого розгортання.
