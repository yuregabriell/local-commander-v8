Nova TV v0.3 — Safe preview based on Free My TV (MIT)
========================================================
Instalação: Android TV 11, como APK normal a partir de um gestor de ficheiros.
Executa diretamente a nova interface. NÃO assume o papel HOME nem modifica
componentes do sistema, pelo que o launcher original da Hisense continua ativo.
O botão HOME do comando regressa ao launcher original.

Upstream: https://github.com/simbasjungles/free-my-tv
Upstream pinned commit: 4d739516379976e4cc256e4106f3680d6bb94da8
License: MIT; copyright and license retained in MIT_LICENSE_UPSTREAM.txt e dentro do APK.
WebOS fork visual reference: https://github.com/danielthejoker18/FreeMyTvWebOS

Alterações próprias: configuração Portugal/Lisboa; entrada diretamente na interface;
remover categoria HOME e desativar jobs de alteração do sistema, boot receiver e
serviço de acessibilidade. As alterações são aplicadas durante a build através de
novatv-free-base/patch_safe_preview.py no repositório de trabalho.

Esta versão é uma EXPERIÊNCIA VISUAL: não é ainda um port pixel-perfect do fork
FreeMyTvWebOS, nem contém integração com o Nova TV Remote, e recomendações externas
de filmes dependem de uma chave TMDB não incluída.

Caso instales versões futuras de teste com outra assinatura, pode ser necessário
desinstalar primeiro este APK. Não altera o CleanYourTV.
