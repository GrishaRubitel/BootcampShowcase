# README

## НЕ РАБОТАЕТ!

Репозиторий с проектом, готовым (почти) к сдаче. Для его запуска клонируем/качаем себе этот репозиторий и ставим Docker Desktop и WSL

1. [https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/)
2. [https://learn.microsoft.com/ru-ru/windows/wsl/install](https://learn.microsoft.com/ru-ru/windows/wsl/install)

Далее открываем терминал в корне (там где лежит **docker-compse.yml**), прописываем следующие команды и поднимаем проект

```powershell
docker-compose build
docker-compose up
```

Изменение кода сервисов (не docker-compose или nginx.conf) требует повторной сборки **docker-compose**
