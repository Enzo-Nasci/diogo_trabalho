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

## 2. Ajustar a aplicação antes de usar o Render

O Render fala na porta da variável `PORT` e faz health check em `/`. Do jeito que o código está, os dois falham. Antes do serviço público:

1. Em `precos/src/main/resources/application.properties`, deixe também a linha `server.port=${PORT:8080}`.
2. Crie um `GET /health` que responda 200 sem parâmetros.
3. No serviço do Render, Health Check Path = `/health`.

Sem isso o container local em 8080 continua válido, e o endereço do Render não.

## 3. Conferir o CI no GitHub

A raiz do repositório no GitHub tem de ser esta pasta, com `precos/` e `.github/` juntos. Abra um pull request para `main`. O job `ci` roda os testes. O job `cd` fica skipped: ele só existe em push na `main` depois do CI verde.

## 4. Publicar a imagem

Faça o merge ou um push na `main`. O job `cd` constrói `precos/Dockerfile` e envia `ghcr.io/<seu-usuário-em-minúsculas>/precos:<sha>`. Na primeira vez o passo do Render falha de propósito, porque o secret `RENDER_DEPLOY_HOOK_URL` ainda não existe. A imagem, nessa altura, já está no GitHub Packages.

No pacote `precos`, deixe a visibilidade pública ou crie um PAT com permissão `read:packages`. O Render precisa de uma das duas coisas para puxar a imagem. O token do workflow expira no fim do job e não serve como credencial do serviço.

## 5. Criar o Web Service no Render

1. New → Web Service → Existing Image.
2. Image URL: `ghcr.io/<seu-usuário-em-minúsculas>/precos` com uma tag que já exista (a do SHA publicado no passo 4).
3. Se o pacote for privado, informe o registry credential (usuário do GitHub e o PAT).
4. Não crie Render Postgres. Este backend não conecta em banco.
5. Health Check Path: `/health`, depois do ajuste do passo 2.
6. Em Settings → Auto-Deploy, escolha Off. Assim só o workflow publica, e só depois do CI.
7. Copie o Deploy Hook. É uma URL secreta. Não cole no Git.

## 6. Ligar o hook e rodar de novo

No GitHub: Settings → Secrets and variables → Actions → New repository secret.

- Nome: `RENDER_DEPLOY_HOOK_URL`
- Valor: a URL do deploy hook

Abra o workflow que falhou no passo 4 e use Re-run. O job `cd` faz o POST com `imgURL` igual à imagem daquele commit. O nome `ghcr.io/<owner>/precos` tem de ser o mesmo configurado no serviço. Só a tag muda.

Quando o deploy ficar live, teste:

```text
https://<seu-servico>.onrender.com/compra?nome=frango&quantidade=2
https://<seu-servico>.onrender.com/health
```
