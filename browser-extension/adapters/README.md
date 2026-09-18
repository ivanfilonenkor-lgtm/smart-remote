# Адаптеры сайтов

Каждый адаптер регистрируется через `SmartRemoteVideo.register(...)` и реализует небольшой
контракт:

```js
SmartRemoteVideo.register({
  id: "stable_site_id",
  name: "Название в телефоне",
  matches(locationLike) { return locationLike.hostname === "example.org"; },
  readState() {
    return {
      title: null,
      episode: null,
      episodeCount: null,
      previousAvailable: false,
      nextAvailable: false,
      nativeAutoNext: false,
      nativeAutoMode: null
    };
  },
  execute(action, payload) { return false; }
});
```

`execute` может обработать `previous`, `next` и `set_auto_mode`. Play/pause и явно подписанная
кнопка пропуска опенинга уже реализованы общим движком для обычного HTML5 `<video>`.

Чтобы добавить сайт:

1. Создайте `adapters/<site>.js`.
2. Добавьте файл после `adapters/registry.js` в список `content_scripts.js`.
3. Добавьте только нужные домены в `host_permissions` и `content_scripts.matches`.
4. Добавьте smoke-тест DOM-разметки сайта в `tests/`.
5. Увеличьте версию расширения и проверьте его после обновления сайта.

Селекторы сайта должны находиться только в его адаптере. Автоматически нажимать общую кнопку
«Пропустить» нельзя: она может относиться к рекламе. Для опенинга допускается только явная
семантика intro/opening/опенинг/вступление.
