# Pomotiva — Template Android 2025

Aplicativo Android de produtividade com Pomodoro, metas e estatísticas, integrado ao Firebase. Este template oferece uma base moderna para apps com autenticação, armazenamento na nuvem, gráficos e UI Material.

## Sumário
- **Visão geral**
- **Funcionalidades**
- **Stack e versões**
- **Como executar**
- **Configuração do Firebase**
- **Estrutura do projeto**
- **Notas sobre estatísticas e metas**
- **Testes**
- **Contribuição**

## Visão geral
O app permite:
- Login/cadastro com Firebase Authentication (incluindo Google Sign-In).
- Temporizador Pomodoro com contagem de ciclos e registro de foco/pausas.
- Definição de metas por período (diário/semanal/mensal/anual).
- Estatísticas por período com gráfico (MPAndroidChart) e KPIs.

## Funcionalidades
- **Autenticação**: Email/senha e Google (Play Services Auth).
- **Pomodoro**: Work/Break com notificação de término via WorkManager.
- **Metas**: Configuração de `cycles_target` por período em `users/{uid}/pomodoro/goals`.
- **Estatística**:
  - Gráfico de barras com minutos focados por dia.
  - KPIs de tempo focado, sessões e taxa de conclusão por período.
  - Taxa de conclusão = soma de ciclos no período / meta de ciclos do mesmo período.

## Stack e versões
- Android Gradle Plugin (via Version Catalog `libs`)
- Compile SDK: 35 | Target SDK: 35 | Min SDK: 24
- Kotlin/JVM: 11
- Navegação: AndroidX Navigation
- Coroutines: kotlinx-coroutines
- Gráficos: MPAndroidChart v3.1.0
- Imagens: Glide
- Tarefas em background: WorkManager 2.9.0
- Firebase (BOM + KTX): Auth, Realtime Database, Firestore, Storage, Common
- Google Play Services Auth (Google Sign-In)

## Como executar
1. Clone o repositório
   ```bash
   git clone https://github.com/SEU-USUARIO/AndroidAppTemplate2025_2.git
   ```
2. Abra no Android Studio (Giraffe+ recomendado) e aguarde a sincronização do Gradle.
3. Adicione o arquivo `google-services.json` em `app/`.
4. Execute em um dispositivo/emulador (API 24+).

## Configuração do Firebase
1. Crie um projeto no Firebase Console e adicione um app Android com o `applicationId`:
   - `com.ifpr.androidapptemplate`
2. Baixe o `google-services.json` e coloque em `app/`.
3. Ative produtos necessários:
   - Authentication (Email/Password e Google)
   - Realtime Database (ou Firestore, se pretende usar features correlatas)
   - Storage (para avatares)
4. Google Sign-In:
   - Cadastre os SHA-1/SHA-256 do seu projeto (Debug/Release) no Firebase.
   - Verifique o provedor Google em Authentication.

## Estrutura do projeto (resumo)
```
app/
  src/main/java/com/ifpr/androidapptemplate/
    ui/
      estatistica/      # Estatística (gráfico e KPIs)
      metas/            # Metas por período
      pomodoro/         # Timer, repositório, modelos e workers
    ...
  src/main/res/
    layout/             # Layouts XML (inclui fragment_estatistica.xml)
  build.gradle.kts      # Dependências do app
build.gradle.kts        # Configuração raiz
``` 

## Notas sobre estatísticas e metas
- Períodos suportados: Hoje (1 dia), Semana (7), Mês (30), Ano (365).
- As metas são lidas de `users/{uid}/pomodoro/goals` com chaves:
  - `daily_cycles_target`, `weekly_cycles_target`, `monthly_cycles_target`, `yearly_cycles_target`
  - Internamente, o app utiliza `fetchPeriod(prefix)` com `daily`, `weekly`, `monthly`, `yearly`.
- Taxa de conclusão (KPI):
  - Calculada a partir da soma de ciclos no período selecionado, dividida pela meta de ciclos do mesmo período.
  - Exibe 0% quando a meta do período é 0.

## Testes
- Unit tests com JUnit e Mockito.
- Coroutines Test para cenários assíncronos.
  ```bash
  ./gradlew test
  ```

## Contribuição
1. Crie uma branch feature/fix.
2. Siga o estilo de commit convencional (ex.: `feat(...)`, `fix(...)`, `chore(...)`).
3. Abra um Pull Request descrevendo a mudança e como testar.