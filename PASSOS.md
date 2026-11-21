# Passos para testar e publicar

O pipeline sobe só a imagem Docker do backend. Não há banco para subir.

## 1. Testar na sua máquina

Na pasta `precos`:

```powershell
.\mvnw.cmd -B test
```

O resultado esperado é `BUILD SUCCESS` com o teste `contextLoads`.

Com o Docker Desktop aberto, na raiz do repositório:

```powershell
docker build -t precos:local precos
docker run --rm -p 8080:8080 precos:local
```

Em outro terminal:

```powershell
curl "http://localhost:8080/compra?nome=frango&quantidade=2"
```

Resposta esperada: HTTP 200, produto `frango`, preço `10.0`, `desconto` falso e total `20.0`. Com `quantidade=10` o desconto fica verdadeiro e o total vai a `95.0`. `nome` fora de `frango`, `peru` e `chester`, ou quantidade menor ou igual a zero, deve falhar.

## 2. Porta e health check

`application.properties` já tem `server.port=${PORT:8080}`. Sem a variável `PORT`, o processo local continua na 8080. No Render, a plataforma preenche `PORT`.

O `GET /health` já responde 200 sem parâmetros. No serviço, Health Check Path = `/health`. O teste do negócio continua sendo `/compra?nome=frango&quantidade=2`.

## 3. Conferir o CI no GitHub

A raiz do repositório no GitHub tem de ser esta pasta, com `precos/` e `.github/` juntos. Abra um pull request para `main`. O job `ci` roda os testes. O job `cd` fica skipped: ele só existe em push na `main` depois do CI verde.

## 4. Publicar a imagem

Faça o merge ou um push na `main`. O job `cd` constrói `precos/Dockerfile` e envia `ghcr.io/<seu-usuário-em-minúsculas>/precos:<sha>`. Na primeira vez o passo do Render falha de propósito, porque o secret `RENDER_DEPLOY_HOOK_URL` ainda não existe. A imagem, nessa altura, já está no GitHub Packages.

O Render deste serviço não puxa essa imagem do GHCR. Ele é ambiente Docker e constrói `precos/Dockerfile` a partir do repositório. A imagem no GHCR é a cópia publicada pelo workflow.

## 5. Conferir o Web Service no Render

O serviço já é ambiente Docker. Por isso o hook com `imgURL` respondeu 400: `docker environment does not support image URLs`. Não recrie o serviço como Existing Image para esse hook.

1. Settings → Dockerfile Path = `precos/Dockerfile`. O arquivo não está na raiz do repositório.
2. Não crie Render Postgres. Este backend não conecta em banco.
3. Health Check Path = `/health`.
4. Se Auto-Deploy aparecer, deixe Off. Ele só existe porque o serviço está ligado ao Git. Com Off, só o workflow publica, e só depois do CI.
5. Copie o Deploy Hook em Settings. É uma URL secreta. Não cole no Git.

## 6. Ligar o hook e rodar de novo

No GitHub: Settings → Secrets and variables → Actions → New repository secret.

- Nome: `RENDER_DEPLOY_HOOK_URL`
- Valor: a URL do deploy hook

Abra o workflow que falhou e use Re-run. O job `cd` faz o POST na URL do hook, sem `imgURL`. O Render inicia um build do `precos/Dockerfile` do commit ligado ao serviço.

Quando o deploy ficar live, teste:

```text
https://<seu-servico>.onrender.com/compra?nome=frango&quantidade=2
https://<seu-servico>.onrender.com/health
```
