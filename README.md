# MediTrack AI

<p align="center">
  🌐 <strong>Українська</strong> · <a href="README.en.md">English</a>
</p>

MediTrack AI — навчальний Android-застосунок для обліку ліків і нагадувань про прийом. Він також допомагає віднести опис самопочуття до загальної категорії та пояснює результат.

<p align="center">
  <a href="https://github.com/Nazarlo3/MediTrack-AI/releases/download/v0.5.1/MediTrackAI-0.5.1-release.apk"><img src="https://img.shields.io/badge/%D0%97%D0%B0%D0%B2%D0%B0%D0%BD%D1%82%D0%B0%D0%B6%D0%B8%D1%82%D0%B8%20APK-0.5.1-6f42c1?style=for-the-badge&amp;logo=github&amp;logoColor=white" alt="Завантажити APK 0.5.1"></a>
</p>

[![Реліз GitHub](https://img.shields.io/github/v/release/Nazarlo3/MediTrack-AI)](https://github.com/Nazarlo3/MediTrack-AI/releases/latest)
[![Перевірки Android і backend](https://github.com/Nazarlo3/MediTrack-AI/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nazarlo3/MediTrack-AI/actions/workflows/ci.yml)
[![Android 10 і новіші](https://img.shields.io/badge/Android-10%2B-3DDC84)](#встановлення)

[Релізи](https://github.com/Nazarlo3/MediTrack-AI/releases) · [Документація](docs/)

## Можливості

- Список препаратів, фото, кілька прийомів на день і позначки виконання.
- Нагадування за розкладом і підсумок прийомів за день.
- AI-категоризація за описом із необов’язковими полями тривалості й інтенсивності.
- Уточнення за браку інформації та повтор після помилки з’єднання.
- До 20 результатів у локальній історії з переглядом і видаленням.
- Світла й темна теми, діагностика з’єднання та перемикач збору даних.

<p align="center">
  <img src="docs/assets/screenshots/medications.png" alt="Мої ліки з підсумком прийомів за день" width="200">
  <img src="docs/assets/screenshots/analysis.png" alt="Приклад категоризації у вкладці Аналіз AI" width="200">
  <img src="docs/assets/screenshots/settings.png" alt="Налаштування теми, збору даних і діагностики" width="200">
</p>

На знімках використано синтетичні дані.

## Встановлення

Завантажте підписаний APK із Releases і відкрийте його на пристрої з Android 10 або новішим. За потреби дозвольте встановлення з джерела, через яке відкриваєте файл. Офіційний APK 0.5.1 має той самий підпис, що й 0.5. Якщо встановлено збірку з іншим підписом, спочатку ознайомтеся з [приміткою про сумісність](docs/guides/development.md#підпис-release).

AI-аналіз потребує інтернету та надсилає введений опис на сервер. Історію можна переглядати без мережі. Перемикач «Допомагати покращувати AI» керує окремим збереженням нових звернень для оцінювання в Google Sheets. Початково він увімкнений. [Як обробляються дані](docs/data-collection.md).

> Застосунок призначений для навчальної демонстрації. Результат AI не є діагнозом і не замінює консультацію лікаря.

## Розробка

Відкрийте корінь проєкту в Android Studio, виконайте Gradle Sync і запустіть модуль `app`. Типова debug-збірка звертається до сервера на Render. Локальний запуск FastAPI, підключення телефона чи емулятора й команди перевірок описані в [інструкції розробника](docs/guides/development.md).

```mermaid
flowchart LR
    Android[Android · Kotlin і Compose] -->|HTTPS / JSON| API[FastAPI]
    API --> Groq[Groq AI]
    Android --> Storage[(Локальна історія та ліки)]
```

| Папка | Вміст |
| --- | --- |
| `app/` | Android-клієнт, модульні та інструментальні тести |
| `backend/` | FastAPI, AI-провайдери й тести сервера |
| `docs/` | Архітектура, API та перевірки |
| `.github/` | CI та шаблони для учасників |

- [Огляд проєкту](docs/project-overview.md) · [Архітектура](docs/architecture.md) · [API](docs/api-contract.md).
- [Відомі обмеження](docs/known-limitations.md) · [Перевірки](docs/testing.md).
- [Повідомити про помилку](https://github.com/Nazarlo3/MediTrack-AI/issues/new/choose) · [Запропонувати зміни](.github/CONTRIBUTING.md) · [Приватно повідомити про вразливість](SECURITY.md).
