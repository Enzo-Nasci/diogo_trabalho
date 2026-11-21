# Imprecisões do CI/CD como está agora

O manifesto Kubernetes e a imagem de Postgres saíram do pipeline. O backend não tem banco: o `pom.xml` não declara JPA nem driver PostgreSQL, e `application.properties` só tem `spring.application.name=precos`. Os preços ficam no enum `Product`. O CD constrói e publica somente `precos/Dockerfile`, e o deploy no Render é um POST no deploy hook com essa imagem.

O que segue ainda impede o endereço público de funcionar só com o que está no Git. Nenhuma dessas correções foi aplicada no código da aplicação.

## 1. O deploy hook ainda não existe

O passo “Deploy da imagem do backend no Render” lê `secrets.RENDER_DEPLOY_HOOK_URL`. Sem esse secret o job termina vermelho depois de publicar a imagem no GHCR. O workflow não cria o Web Service: isso é feito no painel do Render.

**Como resolver:** seguir `PASSOS.md` (serviço Docker a partir de `ghcr.io/<owner>/precos`, Auto-Deploy desligado, secret no GitHub, rodar o job de novo). O `imgURL` que o workflow envia precisa ser a mesma imagem configurada no serviço, mudando só a tag. Se o serviço estiver apontando para outro nome, o Render recusa o deploy.

## 2. A aplicação não escuta a porta do Render

O Dockerfile faz `EXPOSE 8080`. Sem `server.port`, o Spring Boot sobe em 8080. O Render injeta `PORT` (em geral 10000) e o proxy fala com essa porta. A imagem sobe e o serviço não recebe tráfego.

**Como resolver:** em `precos/src/main/resources/application.properties`, acrescentar `server.port=${PORT:8080}`. Localmente, sem a variável `PORT`, continua 8080.

## 3. Não há rota de health

O único endpoint é `GET /compra`, e ele exige `nome` e `quantidade`. O health check padrão do Render bate em `/` e recebe 404, então o deploy pode ser marcado como falho.

**Como resolver:** uma rota que responda 200 sem parâmetros, por exemplo `GET /health`, e configurar esse path como Health Check Path do serviço. O teste funcional do negócio continua sendo `/compra?nome=frango&quantidade=2`.

## 4. O CI não exercita a regra de negócio

`./mvnw -B test` executa só `PrecosApplicationTests.contextLoads`. Desconto a partir de 10 unidades, quantidade inválida e nome inválido não têm teste. O build da imagem usa `-DskipTests`, então o CD não recupera essa lacuna.

**Como resolver:** testes de `ProdutosService` e do controller. Não criei esses testes.

## 5. A imagem no GHCR nasce privada

O `docker push` usa o `GITHUB_TOKEN` do job (`packages: write`). O pacote fica privado. O Render não puxa essa imagem sem credencial de registry, e o token do job acaba quando o workflow termina.

**Como resolver:** no pacote do GitHub, deixar a imagem pública, ou no serviço do Render informar um PAT com `read:packages`. O passo a passo está em `PASSOS.md`.

## 6. Dois deploys se o Auto-Deploy do Render estiver em On Commit

A trava `needs: ci` vale para o job `cd`. Se o Web Service estiver com Auto-Deploy “On Commit”, o push na `main` também publica direto pelo Render, antes do CI deste workflow.

**Como resolver:** Auto-Deploy Off, e só o job `cd` chama o hook. A alternativa “After CI Checks Pass” também espera o CI, mas aí o workflow não deve disparar um segundo deploy.

## 7. O Actions só lê `.github` na raiz do repositório

O workflow está em `.github/workflows/ci-cd.yml`, ao lado da pasta `precos/`. Se o remoto no GitHub tiver só o conteúdo de `precos/` como raiz, o Actions ignora o arquivo.

**Como resolver:** a raiz do repositório é esta pasta, com `precos/` e `.github/` no mesmo nível.
