# Imprecisões do CI/CD como está agora

O manifesto Kubernetes e a imagem de Postgres saíram do pipeline. O backend não tem banco: o `pom.xml` não declara JPA nem driver PostgreSQL, e os preços ficam no enum `Product`. O CD constrói `precos/Dockerfile`, publica essa imagem no GHCR e pede o deploy no Render com um POST no deploy hook, sem `imgURL`.

O serviço no Render é ambiente Docker. Enviar `imgURL` nesse hook devolve HTTP 400: `docker environment does not support image URLs`. O workflow não envia mais esse parâmetro. O Render constrói o Dockerfile do repositório. A imagem do GHCR não é a que ele executa.

`application.properties` já tem `server.port=${PORT:8080}` e o controller já expõe `GET /health`.

## 1. O Dockerfile do Render tem de ser o do módulo

O `Dockerfile` está em `precos/Dockerfile`, não na raiz. Se o serviço Docker procurar o arquivo na raiz, o build do hook não acha o backend.

**Como resolver:** em Settings, Dockerfile Path = `precos/Dockerfile`. Health Check Path = `/health`.

## 2. O secret do hook continua obrigatório

O passo de deploy lê `secrets.RENDER_DEPLOY_HOOK_URL`. Sem ele o job termina vermelho depois de publicar a imagem no GHCR.

**Como resolver:** copiar o Deploy Hook em Settings e gravar no GitHub com esse nome. Não colocar `imgURL` na URL.

## 3. O CI não exercita a regra de negócio

`./mvnw -B test` executa só `PrecosApplicationTests.contextLoads`. Desconto a partir de 10 unidades, quantidade inválida e nome inválido não têm teste. O build da imagem usa `-DskipTests`, então o CD não recupera essa lacuna.

**Como resolver:** testes de `ProdutosService` e do controller. Não criei esses testes.

## 4. A imagem no GHCR nasce privada

O `docker push` usa o `GITHUB_TOKEN` do job (`packages: write`). O pacote fica privado. O serviço Docker do Render não puxa esse pacote, então isso não causa o 400. Passa a importar só se alguém for baixar `ghcr.io/<owner>/precos` de fora do workflow.

**Como resolver:** deixar o pacote público, ou usar um PAT com `read:packages`, se essa imagem for usada fora do Actions.

## 5. Auto-Deploy em serviço ligado ao Git

Este serviço está ligado ao repositório, então Auto-Deploy pode aparecer em Settings. Em On Commit, o push na `main` publica pelo Render em paralelo com o job `cd`, antes do CI. A trava `needs: ci` vale só para o workflow.

**Como resolver:** Auto-Deploy Off. Só o POST do job `cd` dispara o build.

## 6. O Actions só lê `.github` na raiz do repositório

O workflow está em `.github/workflows/ci-cd.yml`, ao lado da pasta `precos/`. Se o remoto no GitHub tiver só o conteúdo de `precos/` como raiz, o Actions ignora o arquivo.

**Como resolver:** a raiz do repositório é esta pasta, com `precos/` e `.github/` no mesmo nível.
