# Acervo — Catálogo de Livros

Aplicação web CRUD de uma biblioteca pessoal. O navegador conversa com uma API REST em Java 17 e Spring Boot, e os livros ficam em um banco H2 na memória enquanto a aplicação está em execução.

## Domínio

Cada registro representa um livro do acervo.

| Campo | Obrigatório | Regra |
|---|---|---|
| Título | Sim | Até 150 caracteres |
| Autor | Sim | Até 120 caracteres |
| ISBN | Sim | 10 ou 13 dígitos; hífens opcionais; único |
| Ano de publicação | Sim | De 1450 até o ano atual |
| Gênero | Sim | Até 40 caracteres |
| Editora | Não | Até 120 caracteres |
| Exemplares | Sim | Inteiro de 0 a 10000 |

O ISBN é gravado sem hífens. `978-85-359-1484-2` e `9788535914842` são o mesmo código.

## Funcionalidades

- Cadastrar um livro e receber o identificador gerado.
- Listar o catálogo e consultar um livro pelo id.
- Atualizar um livro existente.
- Excluir um livro, com confirmação na interface.
- Validar os dados e informar quando o id ou o ISBN não puder ser usado.

## Tecnologias

- Java 17
- Spring Boot 3.4.5
- Spring Web
- Spring Data JPA
- Bean Validation
- H2 Database
- Maven
- HTML5, CSS3 e JavaScript (Fetch API)

## Arquitetura

```text
biblioteca-crud/
├── exemplos/
│   ├── criar-livro.json
│   └── atualizar-livro.json
├── src/main/java/com/biblioteca/acervo/
│   ├── BibliotecaApplication.java
│   ├── config/          CORS da API
│   ├── controller/      endpoints REST
│   ├── dto/             entrada e saída da API
│   ├── exception/       erros e respostas HTTP
│   ├── model/           entidade Livro
│   ├── repository/      Spring Data JPA
│   └── service/         regras de negócio
├── src/main/resources/
│   ├── application.properties
│   └── static/          interface web
├── src/test/java/       testes do ciclo CRUD
├── pom.xml
└── README.md
```

A interface fica em `src/main/resources/static` e é servida pelo próprio Spring Boot. O controller não aplica regra de negócio: ele recebe a requisição, chama o service e devolve o status HTTP.

## Pré-requisitos

- JDK 17 instalado e disponível no `PATH` (ou `JAVA_HOME` apontando para o JDK 17).
- Maven 3.9 ou superior. Se o Maven não estiver instalado, use o wrapper depois de gerá-lo, ou instale pelo site do Apache Maven.
- Navegador atualizado.

Confira as versões:

```powershell
java -version
mvn -version
```

A saída do Java deve indicar a versão 17. O `JAVA_HOME` precisa apontar para o JDK, não apenas para o `java` do sistema.

### Instalar o Java 17 no Windows

1. Baixe o Temurin 17 em [https://adoptium.net/temurin/releases/?version=17](https://adoptium.net/temurin/releases/?version=17).
2. Execute o instalador e marque a opção de definir `JAVA_HOME`.
3. Abra um novo terminal e execute `java -version`.

### Instalar o Maven no Windows

1. Baixe o binário em [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi).
2. Extraia, por exemplo, em `C:\apache-maven`.
3. Crie a variável `MAVEN_HOME` com essa pasta e inclua `%MAVEN_HOME%\bin` no `PATH`.
4. Abra um novo terminal e execute `mvn -version`.

## Importar na IDE

### IntelliJ IDEA

1. File → Open e selecione a pasta `biblioteca-crud` (a pasta que contém o `pom.xml`).
2. Confirme a importação como projeto Maven.
3. Em File → Project Structure, defina o SDK como Java 17.
4. Abra `BibliotecaApplication` e execute o `main`.

### Eclipse

1. File → Import → Maven → Existing Maven Projects.
2. Selecione a pasta do projeto e finalize.
3. Confirme que o projeto usa o JRE Java 17.
4. Run As → Spring Boot App, ou execute `BibliotecaApplication`.

### VS Code / Cursor

1. Abra a pasta do projeto.
2. Instale a extensão Extension Pack for Java.
3. Selecione o JDK 17 quando a extensão solicitar o runtime.
4. Execute `BibliotecaApplication` pelo botão Run, ou use o terminal com Maven.

## Executar

No diretório do projeto:

```powershell
mvn spring-boot:run
```

Também é possível gerar o jar e executá-lo:

```powershell
mvn clean package
java -jar target/biblioteca-crud-1.0.0.jar
```

Os testes automatizados cobrem cadastro, listagem, busca, atualização, exclusão, validação, id inexistente e ISBN duplicado:

```powershell
mvn test
```

## Endereços

| Recurso | Endereço |
|---|---|
| Interface | [http://localhost:8080](http://localhost:8080) |
| API | [http://localhost:8080/api/livros](http://localhost:8080/api/livros) |
| Console H2 | [http://localhost:8080/h2-console](http://localhost:8080/h2-console) |

Abra a interface pelo endereço acima. Abrir o arquivo `index.html` direto do disco não alcança a API.

## Console H2

Com a aplicação em execução, acesse [http://localhost:8080/h2-console](http://localhost:8080/h2-console) e informe:

| Campo | Valor |
|---|---|
| Saved Settings | Generic H2 (Embedded) |
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:biblioteca` |
| User Name | `sa` |
| Password | deixe em branco |

Depois de conectar, a tabela `LIVROS` mostra os registros criados pela interface ou pela API. O banco é em memória: ao encerrar a aplicação, os dados são descartados. Durante a execução, `DB_CLOSE_DELAY=-1` mantém o banco vivo para o console consultar a mesma base da API.

Consulta útil:

```sql
SELECT * FROM LIVROS;
```

## Endpoints

| Método | Endpoint | Sucesso | Erros comuns |
|---|---|---|---|
| GET | `/api/livros` | 200 e a lista, ou `[]` quando vazia | — |
| GET | `/api/livros/{id}` | 200 e o livro | 400 id inválido, 404 inexistente |
| POST | `/api/livros` | 201, cabeçalho `Location` e o livro criado | 400 inválido, 409 ISBN duplicado |
| PUT | `/api/livros/{id}` | 200 e o livro atualizado | 400 inválido, 404 inexistente, 409 ISBN de outro livro |
| DELETE | `/api/livros/{id}` | 204 sem corpo | 404 inexistente |

Uma lista vazia é uma consulta bem-sucedida: a API responde `200` com `[]` e a tela exibe “Nenhum livro cadastrado”.

## Exemplos de teste

Os arquivos em `exemplos/` repetem os corpos abaixo. Troque `{id}` pelo identificador retornado no cadastro. Os exemplos usam `curl.exe`, disponível no Windows.

### Create — 201 Created

```powershell
curl.exe -i -X POST http://localhost:8080/api/livros -H "Content-Type: application/json" --data-binary "@exemplos/criar-livro.json"
```

Corpo enviado:

```json
{
  "titulo": "O Cortiço",
  "autor": "Aluísio Azevedo",
  "isbn": "978-85-359-1484-2",
  "anoPublicacao": 1890,
  "genero": "Romance",
  "editora": "Penguin Companhia",
  "quantidade": 2
}
```

Resposta esperada:

```http
HTTP/1.1 201
Location: http://localhost:8080/api/livros/1
```

```json
{
  "id": 1,
  "titulo": "O Cortiço",
  "autor": "Aluísio Azevedo",
  "isbn": "9788535914842",
  "anoPublicacao": 1890,
  "genero": "Romance",
  "editora": "Penguin Companhia",
  "quantidade": 2
}
```

### Read — listar e buscar

```powershell
curl.exe -i http://localhost:8080/api/livros
curl.exe -i http://localhost:8080/api/livros/1
```

Sem registros, a listagem responde:

```http
HTTP/1.1 200
```

```json
[]
```

Id inexistente:

```http
HTTP/1.1 404
```

```json
{
  "status": 404,
  "erro": "Not Found",
  "mensagem": "Livro não encontrado para o id 999.",
  "caminho": "/api/livros/999"
}
```

### Update — 200 OK

```powershell
curl.exe -i -X PUT http://localhost:8080/api/livros/1 -H "Content-Type: application/json" --data-binary "@exemplos/atualizar-livro.json"
```

A resposta devolve o mesmo `id` e a quantidade `5`.

### Delete — 204 No Content

```powershell
curl.exe -i -X DELETE http://localhost:8080/api/livros/1
```

A resposta não tem corpo. Uma segunda exclusão do mesmo id responde `404`.

### Validação — 400 Bad Request

```powershell
curl.exe -i -X POST http://localhost:8080/api/livros -H "Content-Type: application/json" -d "{\"titulo\":\" \"}"
```

```json
{
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Verifique os campos informados.",
  "caminho": "/api/livros",
  "campos": [
    { "campo": "titulo", "mensagem": "O título é obrigatório." }
  ]
}
```

Um segundo cadastro com o mesmo ISBN responde `409 Conflict`.

## Interface

1. Abra [http://localhost:8080](http://localhost:8080).
2. Preencha a ficha e clique em **Cadastrar livro**. O livro aparece no catálogo.
3. Clique em **Editar**. A ficha recebe os dados daquele registro; **Salvar alterações** envia o `PUT`.
4. Clique em **Excluir** e confirme. O registro sai da lista.
5. Tente salvar a ficha vazia ou repetir um ISBN para ver as mensagens de erro.
6. No console H2, execute `SELECT * FROM LIVROS` e confira que a tabela acompanhou as operações.

## Solução de problemas

- **A porta 8080 está em uso.** Encerre o outro processo ou altere `server.port` em `application.properties`.
- **O console H2 abre vazio.** Confirme a JDBC URL `jdbc:h2:mem:biblioteca`, o usuário `sa` e a senha vazia, com a aplicação ainda em execução.
- **A interface não grava.** Use `http://localhost:8080`, com o Spring Boot em execução. Não abra o HTML como arquivo local.
- **`mvn` não é reconhecido.** Instale o Maven e abra um novo terminal.
- **A versão do Java não é 17.** Ajuste `JAVA_HOME` para o JDK 17 antes de compilar.
