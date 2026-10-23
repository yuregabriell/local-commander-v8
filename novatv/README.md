# Nova TV — build para Hisense 32A4HA (Android TV 11)
Launcher de teste, totalmente local e leve, inspirado na organização do Projectivy; implementação própria, sem código Projectivy.
- App TV nativo com suporte a comando D-pad, temas, favoritos e wallpaper personalizado por seletor Android.
- Sem root, permissões perigosas, ADB ou alterações ao launcher original.
- Não declara categoria HOME deliberadamente: abre a partir de Apps, para preservar a configuração da Hisense e o CleanYourTV.
- Para construir: gradle -p novatv :app:assembleDebug (Android SDK 35).
- A imagem pessoal requer um seletor de ficheiros compatível instalado na TV; os quatro temas incluídos funcionam sem seletor.
- Pressionar prolongadamente OK num cartão alterna favorito.
- Sem serviços permanentes, publicidade, rastreio ou acesso à Internet.
