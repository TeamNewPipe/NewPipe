<h3 align="center">Nós estamos planejando <i>reescrever</i> grandes pedaços do código base, para gerar <a href="https://github.com/TeamNewPipe/NewPipe/discussions/10118"> um novo, moderno e estável NewPipe</a>!</h3>
<h4 align="center">Por favor, <b>não</b> abra solicitações de pull para <i>novos recursos</i> por enquanto, apenas correções de bugs serão aceitas.</h4>

<p align="center"><a href="https://newpipe.net"><img src="../assets/new_pipe_icon_5.png" width="150"></a></p> 

<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">Uma interface de streaming leve e gratuita para Android.</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-pt-br.svg" alt="Get it on F-Droid" height=80/></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg" ></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="License: GPLv3"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="Build Status"><img src="https://github.com/TeamNewPipe/NewPipe/workflows/CI/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="Translation Status"><img src="https://hosted.weblate.org/widgets/newpipe/-/svg-badge.svg"></a>
<a href="https://web.libera.chat/#newpipe" alt="IRC channel: #newpipe"><img src="https://img.shields.io/badge/IRC%20chat-%23newpipe-brightgreen.svg"></a>
</p>
<hr>
<p align="center"><a href="#screenshots">Screenshots</a> &bull; <a href="#serviços-suportados">Serviços Suportados</a> &bull; <a href="#descrição">Descrição</a> &bull; <a href="#recursos">Recursos</a> &bull; <a href="#instalação-e-atualizações">Instalação e atualizações</a> &bull; <a href="#contribuições">Contribuições</a> &bull; <a href="#doar">Doar</a> &bull; <a href="#licença">Licença</a></p>
<p align="center"><a href="https://newpipe.net">Site</a> &bull; <a href="https://newpipe.net/blog/">Blog</a> &bull; <a href="https://newpipe.net/FAQ/">FAQ</a> &bull; <a href="https://newpipe.net/press/">Press</a></p>
<hr>

*Leia esse documento em outras línguas: [Deutsch](README.de.md), [English](../README.md), [Español](README.es.md), [Français](README.fr.md), [हिन्दी](README.hi.md), [Italiano](README.it.md), [한국어](README.ko.md), [Português Brasil](README.pt_BR.md), [Polski](README.pl.md), [ਪੰਜਾਬੀ ](README.pa.md), [日本語](README.ja.md), [Română](README.ro.md), [Soomaali](README.so.md), [Türkçe](README.tr.md), [正體中文](README.zh_TW.md), [অসমীয়া](README.asm.md), [うちなーぐち](README.ryu.md), [Српски](README.sr.md), [العربية](README.ar.md)*

> [!warning]
> <b>ESTA É UMA VERSÃO BETA, PORTANTO, VOCÊ PODE ENCONTRAR BUGS. ENCONTROU ALGUM, ABRA UM ISSUE ATRAVÉS DO NOSSO REPOSITÓRIO GITHUB PREENCHENDO O MODELO.</b>
>
> <b>COLOCAR NEWPIPE, OU QUALQUER FORK DELE, NA GOOGLE PLAY STORE VIOLA SEUS TERMOS E CONDIÇÕES.</b>

## Screenshots

[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/00.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/00.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png)
<br/><br/>
[<img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width=405>](../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png)
[<img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width=405>](../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png)

### Serviços Suportados

Atualmente, os serviços suportados são:

* YouTube ([site](https://www.youtube.com/)) e YouTube Music ([site](https://music.youtube.com/)) ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([site](https://joinpeertube.org/)) e todas suas instâncias (abra o site para saber o que isso significa!) ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([site](https://bandcamp.com/)) ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([site](https://soundcloud.com/)) ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([site](https://media.ccc.de/)) ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Como você pode ver, o NewPipe suporta múltiplos serviços de vídeo e áudio. Embora tenha começado com o YouTube, outras pessoas adicionaram mais serviços ao longo dos anos, tornando o NewPipe cada vez mais versátil!

Parcialmente devido as circustâncias e a sua popularidade, o YouTube tem o melhor suporte em relação a esses serviços. Se você usa ou é familarizado com qualquer um desses serviços, por favor ajude-nos a melhorar o suporte para eles! Estamos procurando mantenedores para o SoundCloud e o PeerTube.

Se você pretende adicionar um novo serviço, por favor entre em contato conosco primeiro! Nossa [documentação](https://teamnewpipe.github.io/documentation/) traz mais informações em como um novo serviço pode ser adicionado ao aplicativo e no [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Descrição

NewPipe funciona buscando os dados necessários da API oficial (ex. PeerTube) ou do serviço que você está usando. Se a API oficial é restrita (ex. YouTube) para nossos propósitos, ou é proprietária, o aplicativo analisa o site ou usa uma API interna. Isso significa que não é preciso ter uma conta de qualquer serviço para usar o NewPipe.

Também, desde que somos um software livre e de código aberto, nem o aplicativo e nem o Extractor usa qualquer biblioteca ou framework proprietário, como o Google Play Services. Isso significa que você pode usar o NewPipe em dispositivos ou ROMs customizadas em que não tem os aplicativos do Google instalados.

### Recursos

* Assistir vídeos em resoluções de até 4K
* Escutar o áudio em segundo plano, carregando apenas o fluxo de áudio para salvar dados
* Modo popup (player flutuante, aka Picture-in-Picture)
* Assista a transmissões ao vivo
* Mostrar/esconder legendas/closed captions
* Buscar vídeos e áudios (no YouTube, você pode especificar o conteúdo da linguagem também)
* Enfileirar vídeos (e opcionalmente salvar eles como playlists locais)
* Mostrar/esconder informações gerais sobre os vídeos (como descrições e tags)
* Mostrar/esconder vídeos próximos/relacionados
* Mostrar/esconder comentários
* Buscar vídeos, áudios, canais, playlists e álbuns
* Navegar vídeos e áudios dentro de um canal
* Inscrever-se a canais (sim, mesmo se não estiver logado a qualquer conta!)
* Receba notificações sobre novos vídeos de canais em que você está inscrito
* Crie e edite grupos de canais (para facilitar a navegação e o gerenciamento)
* Navege feeds de vídeo gerados a partir dos seus grupos de canais
* Veja e pesquise seu histórico de vídeos
* Pesquise e assista playlists (Eles são playlists remotas, o que significa que eles serão obtidos do serviço que você está navegando)
* Crie e edite playlists locais (Eles são criados e salvos no aplicativo, e não são relacionados com nenhum serviço)
* Baixe vídeos/áudios/legendas (closed captions)
* Abra no Kodi
* Assista/Bloqueie material restrito

## Instalação e atualizações
Você pode instalar NewPipe com um dos seguintes métodos: 
 1. Adicione nosso repo personalizado ao F-Droid e instale-o a partir daí. As instruções estão aqui: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Baixe o APK aqui no [GitHub Releases](https://github.com/TeamNewPipe/NewPipe/releases) e instalá-lo assim que publicarmos um lançamento.
 3. Atualização via F-droid. Este é o método mais lento para obter atualizações, pois o F-Droid deve reconhecer alterações, construir o próprio APK, assiná-lo e, em seguida, enviar a atualização para os usuários.
 4. Construa um APK de depuração você mesmo. Esta é a maneira mais rápida de obter novos recursos em seu dispositivo, mas é muito mais complicado, por isso recomendamos usar um dos outros métodos.
 5. Se você estiver interessado em um recurso específico ou uma correção de bug fornecido em uma solicitação de Pull nesse repositório, pode instalar o APK a partir de lá. Leia a descrição da solicitação para instruções. A grande vantagem dos APKs específicos de S.P é que eles são instalados lado a lado com o aplicativo oficial, então você não precisa se preocupar em perder seus dados ou estragar alguma coisa.

Recomendamos o método 1 para a maioria dos usuários. Os APKs instalados usando o método 1 ou 2 são compatíveis entre si (o que significa que se você instalou o NewPipe usando o método 1 ou 2, você também pode atualizar o NewPipe usando o outro), mas não com aqueles instalados usando o método 3. Isso se deve à mesma chave de assinatura (nossa) sendo usada para 1 e 2, mas uma chave de assinatura diferente (F-Droid's) está sendo usada para 3. Construir um APK depuração usando o método 4 exclui totalmente uma chave. Assinar chaves ajudam a garantir que um usuário não seja enganado para instalar uma atualização maliciosa em um aplicativo. Ao usar o método 5, cada APK é assinado com uma chave aleatória diferente fornecida pelo GitHub Actions, portanto você não pode nem mesmo atualizá-lo. Você terá que fazer backup e restaurar os dados do aplicativo sempre que desejar usar um novo APK.

Enquanto isso, se você quiser trocar de fontes por algum motivo (por exemplo, a funcionalidade principal do NewPipe foi quebrada e o F-Droid ainda não tem a atualização), recomendamos seguir este procedimento:
1. Faça backup de seus dados através de Configurações > Backup e Restauração > Exportar Base de Dados para que você mantenha seu histórico, inscrições e playlists
2. Desinstale o NewPipe
3. Baixe o APK da nova fonte e instale-o
4. Importe os dados da etapa 1 via Configurações > Backup e Restauração > Importar Base de Dados

## Contribuições
Se você tem ideias, traduções, alterações de design, limpeza de códigos ou mudanças reais de código, a ajuda é sempre bem-vinda. O aplicativo fica cada vez melhor a cada contribuição, não importa quão grande ou pequena! Se você quiser se envolver, verifique nossas [notas de contribuição](/.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Estado da tradução" />
</a>

## Doar
Se você gosta do NewPipe, pode enivar uma doação. Nós preferimos Liberapay, pois é de código aberto e sem fins lucrativos. Para mais informações sobre como doar para o NewPipe, visite nosso [site](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="../assets/liberapay_qr_code.png" alt="Visite NewPipe em liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="../assets/liberapay_donate_button.svg" alt="Doar via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Política de Privacidade

O projeto NewPipe tem como objetivo proporcionar uma experiência privada e anônima para o uso de serviços web de mídia.
Portanto, o aplicativo não coleta nenhum dado sem o seu consentimento. A política de privacidade da NewPipe explica em detalhes quais dados são enviados e armazenados quando você envia um relatório de erro ou comenta em nosso blog. Você pode encontrar o documento [aqui](https://newpipe.net/legal/privacy/).

## Licença
[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)  

NewPipe é Software Livre: Você pode usar, estudar compartilhamento e melhorá-lo à sua vontade.
 Especificamente, você pode redistribuir e/ou modificá-lo sob os termos do
[GNU General Public License](https://www.gnu.org/licenses/gpl.html) publicado pela Free Software Foundation, seja a versão 3 da Licença, ou
(a sua opção) qualquer versão posterior.  
