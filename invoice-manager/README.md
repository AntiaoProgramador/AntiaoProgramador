# Trabalho do Davi - Gerenciamento de Notas Fiscais

Projeto inicial com:
- MySQL para armazenamento
- Java (HTTP server simples) para API
- HTML/CSS/JS para site básico de testes

## Banco de dados
1. Suba o MySQL
2. Execute `/home/runner/work/AntiaoProgramador/AntiaoProgramador/invoice-manager/db/schema.sql`

## Variáveis de ambiente
- `DB_URL` (padrão: `jdbc:mysql://localhost:3306/invoice_manager?serverTimezone=UTC`)
- `DB_USER` (padrão: `root`)
- `DB_PASSWORD` (padrão: `root`)
- `PORT` (padrão: `8080`)

## Executar
```bash
cd /home/runner/work/AntiaoProgramador/AntiaoProgramador/invoice-manager
mvn test
mvn compile exec:java
```

Acesse: `http://localhost:8080`
