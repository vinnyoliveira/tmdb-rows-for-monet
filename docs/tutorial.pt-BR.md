# TMDB Rows for Monet — passo a passo

Do zero às linhas na tua TV em cerca de dez minutos.

## O que precisas

- Uma box Android TV / Google TV com **Android 8.0+** e o **Monet Launcher**.
- **Nuvio** ou **Stremio** instalados (são eles que abrem os títulos; esta app não reproduz nada).
- Uma conta gratuita no **TMDB** — dois minutos, e é o que faz as linhas serem certeiras.

## 1. Instalar

Descarrega o `tmdb-rows-for-monet-*.apk` mais recente na página **Releases** e instala na TV
(USB, `adb` ou gestor de ficheiros). Aceita “instalar de fontes desconhecidas” quando o Android pedir.

```bash
adb install -r tmdb-rows-for-monet-1.2.apk
```

## 2. Primeiro arranque: idioma e chave

1. Escolhe o **idioma da app** (Sistema, Português, English, Español, Français). Podes mudar depois
   em **Ajustes → Idioma da app**.
2. Num telemóvel ou computador, abre [themoviedb.org](https://www.themoviedb.org/), cria conta
   gratuita, vai a **Definições → API** e copia a **API Key (v3 auth)**.
3. Na app, foca o campo da chave, prime **OK**, cola e escolhe **Validar e começar**.

A app nunca traz chave do TMDB: a tua fica na TV.

## 3. A primeira linha

1. No início, escolhe **Nova linha**.
2. Mantém **Filtros do TMDB** como origem.
3. Escolhe **Filmes** ou **Séries** e uma seção (Populares, Em alta, Melhor avaliados, …).
4. Dá-lhe um nome ou deixa vazio para usar o automático.
5. Olha para a **prévia** à direita — é exatamente o que vai ser publicado.
6. Escolhe **Salvar e publicar**.

A linha passa a ser um canal do Android TV. Ainda não está no launcher — isso é o passo seguinte.

## 4. Pôr no launcher

1. Abre **Monet → Personalizar**.
2. Adiciona uma **linha de mídia**.
3. Escolhe **TMDB Rows for Monet** e depois o canal **com o nome da tua linha**.

> Cada linha é um canal separado. Se o Monet mostrar uma mistura, escolheste a app em vez da linha —
> escolhe a linha pelo nome.

## 5. As três formas de encher uma linha

### Filtros do TMDB

A mais rápida. Escolhe o tipo, a seção e, em **Filtros avançados**, gêneros, nota, ano e ordem.

### Minha lista

Escolhes os títulos à mão:

- cola IDs do TMDB (`550, 1399`) ou um link (`https://www.themoviedb.org/tv/1399-game-of-thrones`);
- ou usa **Pesquisar no TMDB** e adiciona com **Adicionar**.

Perfeito para “os filmes da minha vida” ou uma lista de novelas.

### Plataforma de streaming

Escolhe uma das principais (Netflix, Prime Video, Disney+, Apple TV, Max, Globoplay…) ou usa o campo
de **pesquisa** para o resto da lista. Os logos vêm do TMDB.

- **Novelas:** Séries · Globoplay · gênero **Novela**.
- Com o Globoplay e os gêneros Notícias / Talk show / Reality aparecem programas, não séries.

## 6. Opções por linha que interessam

- **Seção** — numa plataforma de streaming também podes escolher **Hoje na TV** ou **Novas séries**
  (últimos 30 dias), as mesmas listas do site do TMDB, para o serviço escolhido.
- **Ordem** — mais populares, melhor nota, mais recentes ou **aleatório**. O aleatório volta a
  sortear em cada atualização, ideal para uma linha “surpreende-me”. Em séries há também **Novos
  episódios**, ordenado pela data do episódio mais recente.
- **Arte** — banner 16:9 (recomendado no Monet) ou póster 2:3.
- **Abrir em** — esta linha pode abrir no Nuvio ou no Stremio mesmo que o resto da app use o outro.

## 7. O cartão de capa (opcional)

Cada linha pode começar com um **cartão de capa**: o nome da linha na vertical, numa faixa escura, por
cima de um fundo sorteado da própria linha. Liga ou desliga no editor em **Nome da linha no primeiro
cartão**. Só se aplica a banners 16:9; uma linha chamada *Disney Filmes* começa com `DISNEY FILMES`
deitado, como uma capa de prateleira no launcher.

Os restantes cartões também são compostos no aparelho: backdrop, um scrim escuro, o título (tipo
marca d'água) e uma faixa escura em baixo com as etiquetas — **nota**, **ano** e **gênero**. Todos os
cartões da linha usam os mesmos corpos de letra, por isso nada salta de tamanho. Dois selos podem
aparecer no canto de cima:

- `NOVO` (dourado) para filmes lançados nos últimos 15 dias;
- `NOVO EPISÓDIO` (azul) para séries que transmitiram um episódio nos últimos 3 dias.

Tudo vem do TMDB, por isso segue o idioma escolhido. Liga ou desliga o título, as etiquetas e cada
selo em **Ajustes → Cartões**.

## 8. Idioma e capas

- **Idioma da app** muda só os textos.
- **Pôsteres e títulos** (Ajustes) muda o idioma da arte e dos títulos. Automático segue o aparelho;
  podes escolher qualquer idioma do TMDB (cerca de 180).
- **Capas BetterPosters** (Ajustes → Arte, desligado por omissão) usa cartazes 2:3 do `btttr.cc` com o
  título, o gênero e a nota na imagem, no idioma escolhido acima. Precisa de internet; se falhar, usa
  o póster do TMDB. Só se aplica à arte **Póster 2:3**.
  - **Contras:** é um serviço de terceiros (pode mudar ou sair do ar), acrescenta um pedido extra por
    título ao publicar e ocupa cerca de 70 KB por cartaz na TV.

## 9. Sincronização e bateria

A sincronização automática está **desligada por omissão**: assim a app não agenda nada em segundo
plano e só publica quando tocas em **Atualizar**. Liga em **Ajustes → Sincronização** (6/12/24 h) se
quiseres que as linhas se mantenham frescas sozinhas.

## 10. Atualizar a app

Quando sai uma versão nova, a app mostra um aviso com o número da versão e o botão **Descarregar e
instalar**: descarrega o APK, entrega-o ao Android e o sistema pede confirmação. As linhas e a chave
ficam. Também podes instalar um APK novo à mão por cima do antigo (mesma assinatura). Se desinstalar,
os canais e as definições desaparecem.

## Problemas comuns

**A linha fica vazia.** Vê a prévia: se também diz “Nenhum resultado com esses filtros”, solta um
filtro (nota/ano ou um dos gêneros).

**A linha mostra outros títulos.** No Monet adicionaste a app, não a linha. Remove e adiciona o canal
**com o nome da linha**.

**O cartão abre a app errada.** Define **Ao tocar num póster** nos Ajustes e vê o **Abrir em** da
linha.

**Faltam imagens.** Os banners são desenhados no aparelho; a primeira publicação descarrega a arte.
Dá um minuto e toca em **Atualizar** outra vez.

**A chave deixou de funcionar.** Gera outra no TMDB → Definições → API e substitui nos Ajustes.

**Não aparece nada no launcher.** Publica uma vez (o canal só existe depois da primeira publicação)
e volta a abrir o Monet.

## Perguntas frequentes

**É open source?** Sim — MIT. Qualquer pessoa pode ler, alterar e compilar. A única exigência é a tua
chave do TMDB, que é gratuita.

**Aloja ou transmite conteúdo?** Não. Publica apenas metadados do TMDB; a reprodução acontece no
Nuvio ou no Stremio com as tuas fontes.

**Onde ficam as minhas linhas?** Na TV, no armazenamento privado da app. Sem conta, sem nuvem.

**Porque 50 itens por linha?** Os canais do Android TV não foram feitos para centenas de entradas; 20
é o ponto certo.

**Dá para usar no telemóvel?** Corre, mas a interface é feita para controle remoto.
