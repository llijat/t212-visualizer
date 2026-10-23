# T212 Visualizer — Fase 1 (esqueleto)

Dashboard de cartera sobre la API de T212. Esto es el MVP: conectar, traer
posiciones, mostrarlas en una tabla. Todo lo demás (valoración, calendario,
noticias, QoL) se construye encima de esta base en fases posteriores.

## Qué incluye ya
- Conexión a T212 vía `RestClient` (`T212Client`), apuntando al entorno
  **demo** (paper trading) por defecto — así no arriesgas tu cuenta real
  mientras pruebas.
- Cacheo en Postgres (`Position` entity + `PositionRepository`).
- Un scheduler (`@Scheduled`) que refresca cada 5 minutos, más un botón
  manual en la UI para refrescar al momento.
- Una vista Thymeleaf mínima en `/`.

## Antes de arrancar

1. **Genera tu API Key y Secret** desde la app (Ajustes > API). Al crearla,
   te pedirá qué *scopes* darle — asegúrate de incluir el que cubre
   posiciones/portfolio, o `/equity/positions` te devolverá un 403
   ("Scope(portfolio) missing for API key").

2. **Postgres:** crea la base de datos antes de arrancar:
   ```sql
   CREATE DATABASE t212_visualizer;
   ```
   Ajusta usuario/contraseña vía variables de entorno `DB_USERNAME` /
   `DB_PASSWORD`, o edita directamente `application.yml`.

3. **Variables de entorno:**
   ```bash
   export T212_API_KEY=tu_api_key_aqui
   export T212_API_SECRET=tu_api_secret_aqui
   ```

4. **Nota sobre Pies:** el endpoint `/equity/pies` aparece marcado como
   **deprecated** en la documentación oficial — no construyas nada nuevo
   que dependa de él. Si en el futuro quieres mostrar el desglose por pie,
   tendrás que derivarlo de otra forma (p. ej. cruzando `quantityInPies` de
   cada posición, que si viene en la respuesta de `/equity/positions`).

## Arrancar
```bash
mvn spring-boot:run
```
Luego abre `http://localhost:8080`.

## Roadmap (lo que sigue)

- **Fase 2 — Valoración:** añade un paquete `valuation/` con una interfaz
  `ValuationFormula` (método `apply(Fundamentals) -> ValuationResult`) e
  implementaciones concretas (Graham Number, P/E relativo, DCF simplificado).
  Necesitarás una fuente de fundamentales — ninguno de los endpoints de T212
  los da.
- **Fase 3 — Calendario earnings/dividendos:** integra Finnhub (u otro),
  cruzando los tickers que ya tienes en `positions` contra su endpoint de
  calendario. Guárdalo en una tabla propia (`earnings_events`) y refresca
  una vez al día, no en cada carga de página.
- **Fase 4 — Noticias:** mismo patrón — un `NewsService` que consulta la
  fuente externa filtrando por tus tickers, cacheado para no quemar la
  cuota gratuita.
- **Fase 5 — QoL:** alertas de precio, temas, layouts configurables. Déjalo
  para el final — es pulido, no núcleo.
