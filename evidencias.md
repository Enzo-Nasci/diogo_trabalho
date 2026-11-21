# Evidências do CI/CD

O fluxo está em um único workflow, `.github/workflows/ci-cd.yml`. Cada job roda numa máquina nova do GitHub Actions. O que um job produz não aparece no outro, por isso o `cd` faz checkout de novo.

O CD publica só a imagem Docker do backend (`precos/Dockerfile`). Não há manifesto Kubernetes nem imagem de Postgres: o `pom.xml` não tem driver de banco e `application.properties` não declara datasource.

## CI em pull request e em push na `main`

```3:7:.github/workflows/ci-cd.yml
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
```

`pull_request.branches: [main]` dispara o workflow quando o PR aponta para `main`. `push.branches: [main]` dispara quando o commit chega na `main`. Push em outra branch não entra nessa lista.

## Checkout, Java e testes

O checkout vem antes do teste porque o runner começa vazio. Sem o código do commit, não há `pom.xml` nem `mvnw`.

```22:35:.github/workflows/ci-cd.yml
      - name: Checkout do código do usuário
        uses: actions/checkout@v4

      - name: Instalar Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven
          cache-dependency-path: precos/pom.xml

      - name: Executar testes unitários
        working-directory: precos
        run: chmod +x mvnw && ./mvnw -B test
```

O Java instalado é o 17 porque o módulo declara isso:

```29:31:precos/pom.xml
	<properties>
		<java.version>17</java.version>
	</properties>
```

O `cache-dependency-path` aponta para `precos/pom.xml` porque o `pom.xml` não está na raiz do repositório. O `working-directory: precos` pelo mesmo motivo: `./mvnw` só existe dentro de `precos/`.

O wrapper baixa o Maven sozinho. Em `precos/.mvn/wrapper/maven-wrapper.properties` está `distributionType=only-script` e a URL do Maven 3.9.14. O `.gitignore` do módulo ignora `.mvn/wrapper/maven-wrapper.jar`, então o CI não depende desse jar.

O comando `./mvnw -B test` roda o Surefire em `precos/src/test`. Hoje a única classe é esta:

```6:11:precos/src/test/java/exercicio1/precos/PrecosApplicationTests.java
@SpringBootTest
class PrecosApplicationTests {

	@Test
	void contextLoads() {
	}
```

Rodei o mesmo goal localmente (`mvnw.cmd -B test`): `Tests run: 1, Failures: 0`, `BUILD SUCCESS`. A máquina local está no Java 21; o CI fixa o 17 do `pom.xml`. O compilador usa `release 17`, então o bytecode executado nos dois casos é o da versão do projeto.

O `chmod +x mvnw` existe porque o bit de execução pode se perder no checkout. O `precos/.gitattributes` já pede `eol=lf` para `/mvnw`, que é o que o shell do runner (`ubuntu-latest`) consegue executar.

## CD só depois do CI, e só em push na `main`

```37:44:.github/workflows/ci-cd.yml
  cd:
    name: CD
    needs: ci
    # needs espera o job ci terminar. Sem always(), failure() ou cancelled()
    # no if, o Actions também exige o success() implícito.
    # needs.ci.result == 'success' deixa essa trava escrita na condição.
    # pull_request não entra: o evento não é push e o ref não é refs/heads/main.
    if: ${{ github.event_name == 'push' && github.ref == 'refs/heads/main' && needs.ci.result == 'success' }}
```

Três mecanismos, todos no job `cd`:

1. `needs: ci` impede o `cd` de começar antes do job `ci` terminar. Sem isso, os dois jobs sairiam em paralelo e o deploy poderia publicar um commit que ainda está vermelho.
2. `needs.ci.result == 'success'` é a trava escrita na condição. O GitHub aplica um `success()` implícito quando o `if` não chama `always()`, `failure()` ou `cancelled()`. Essa comparação deixa a regra visível no arquivo e continua valendo se alguém depois acrescentar `always()` na mesma expressão, porque aí o implícito deixa de ser aplicado.
3. `github.event_name == 'push'` junto com `github.ref == 'refs/heads/main'` tira o pull request do CD. Num `pull_request`, `github.ref` é `refs/pull/<número>/merge`, não `refs/heads/main`. O CI desse PR roda; o job `cd` fica skipped.

Se o `ci` falha, `needs.ci.result` é `failure` e o `cd` não constrói a imagem nem chama o Render.

## Só a imagem Docker do backend

O job `cd` constrói `precos/Dockerfile` e publica em `ghcr.io/<owner>/precos:<sha do commit>`:

```54:66:.github/workflows/ci-cd.yml
      - name: Construir a imagem do backend
        run: docker build --platform linux/amd64 --label "org.opencontainers.image.source=https://github.com/${GITHUB_REPOSITORY}" -t "precos:${GITHUB_SHA}" precos

      - name: Publicar a imagem do backend no GHCR
        id: imagem
        run: |
          set -euo pipefail
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${GITHUB_ACTOR}" --password-stdin
          owner="$(echo "${GITHUB_REPOSITORY_OWNER}" | tr '[:upper:]' '[:lower:]')"
          image="ghcr.io/${owner}/precos:${GITHUB_SHA}"
          docker tag "precos:${GITHUB_SHA}" "${image}"
          docker push "${image}"
          echo "name=${image}" >> "${GITHUB_OUTPUT}"
```

`--platform linux/amd64` segue o que o Render executa nos serviços Docker. O Dockerfile compila com JDK 17 e sobe o jar com JRE 17. O `package` da imagem usa `-DskipTests` porque essa imagem só é construída no job que já depende do CI verde. O teste fica no job `ci`.

Não existe segundo `docker build` nem `docker push` de Postgres. O deploy no Render é um POST no deploy hook, sem `imgURL`:

```68:77:.github/workflows/ci-cd.yml
      - name: Deploy do backend no Render
        env:
          RENDER_DEPLOY_HOOK_URL: ${{ secrets.RENDER_DEPLOY_HOOK_URL }}
        run: |
          set -euo pipefail
          if [ -z "${RENDER_DEPLOY_HOOK_URL}" ]; then
            echo "::error::Secret RENDER_DEPLOY_HOOK_URL ausente. A imagem do backend já foi publicada no GHCR. Crie o Web Service e o secret como está em PASSOS.md e rode o job de novo."
            exit 1
          fi
          curl --fail-with-body --silent --show-error --request POST "${RENDER_DEPLOY_HOOK_URL}"
```

O serviço deste projeto no Render é ambiente Docker. Esse tipo recusa `imgURL` com HTTP 400 e a mensagem `docker environment does not support image URLs`. O hook só pede um novo build do `precos/Dockerfile` que já está configurado no serviço. A publicação no GHCR continua no passo anterior e não é o que o Render executa.
