# Local Commander V8 Portable

Local Commander conecta um computador Windows ao ChatGPT para executar operações locais autorizadas, como ler ou editar arquivos, executar comandos, usar Git e automatizar o navegador. A V8 Portable é autocontida: o único launcher é `LocalCommander-V8.bat` e ela não instala Python globalmente nem altera o PATH do Windows.

## Requisitos

- Windows 64 bits;
- conexão com a internet na primeira execução;
- uma conta ChatGPT para o pareamento;
- Git for Windows, somente se você quiser usar operações Git.

## Instalação e uso

1. Baixe ou clone este repositório e mantenha sua estrutura intacta.
2. Dê dois cliques em `LocalCommander-V8.bat`.
3. Na primeira execução, o launcher baixa o Python Embeddable 3.12.10 x64, dependências e, quando disponível, Chromium do Playwright em `runtime/`.
4. Confirme o código de pareamento no navegador usando a mesma conta ChatGPT.
5. Abra o plugin Local Commander no ChatGPT e escolha o computador pareado.

O setup é automático somente na primeira vez. Python portátil fica em `runtime/python/`, o navegador em `runtime/ms-playwright/` e nada é instalado globalmente.

## Como conectar ao ChatGPT

O fluxo recomendado é o Hosted Relay:

`PC → Local Commander V8 → Hosted Relay → plugin Local Commander → ChatGPT`

Ele não exige expor uma porta pública no computador. O guia visual com links de conexão, pareamento e primeiro teste está em [docs/conectar.html](docs/conectar.html).

## Permissões e segurança

Por padrão, a V8 usa o perfil `user`, limitado ao perfil do usuário atual do Windows. Só conecte computadores que você controla e revise pedidos de escrita ou execução antes de autorizá-los. As credenciais de pareamento são protegidas pelo Windows e guardadas localmente em `%APPDATA%\LocalCommanderV8`; não fazem parte deste repositório.

Arquivos de runtime, logs, cookies, tokens, segredos, ambientes virtuais e dados de pareamento são excluídos pelo `.gitignore`. Antes da publicação, o código e a árvore do pacote foram revisados para evitar credenciais ou dados pessoais.

## Atalhos

- `Ctrl+Alt+M` — minimizar
- `Ctrl+Alt+P` — pausar/retomar
- `Ctrl+Alt+D` — atualizar o dashboard
- `Ctrl+Alt+H` — abrir a ajuda
- `Ctrl+Alt+Q` — encerrar

## Remover

Feche com `Ctrl+Alt+Q` e apague a pasta do Local Commander. Para remover também o pareamento local, apague `%APPDATA%\LocalCommanderV8` com o programa fechado.

## Troubleshooting

- **Setup não baixa:** confirme conexão, firewall e antivírus; execute o BAT novamente.
- **Dispositivo offline:** deixe o BAT aberto e confirme que o pareamento foi feito na mesma conta ChatGPT.
- **Pareamento expirou:** com o programa fechado, remova `%APPDATA%\LocalCommanderV8\device.dpapi` e abra o BAT novamente.
- **Automação de navegador indisponível:** abra um Prompt de Comando na pasta do projeto e execute `runtime\python\python.exe -m playwright install chromium`.
- **Git não funciona:** instale o Git for Windows; o programa procura `git.exe` automaticamente.
