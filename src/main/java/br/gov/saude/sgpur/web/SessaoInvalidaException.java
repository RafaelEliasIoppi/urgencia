package br.gov.saude.sgpur.web;

/**
 * Sessao HTTP autenticada (cookie valido), mas sem {@code Usuario}
 * correspondente no banco pelo {@code username} gravado na sessao.
 *
 * <p>Cenario real (bug reportado pelo usuario, Portal do Avaliador): o
 * Spring Security nao rele o {@code UserDetails} a cada requisicao — ele fica
 * fixo na sessao desde o login. Se um ADMIN troca o {@code username} do
 * avaliador (ou exclui a conta) em {@code /usuarios} enquanto ele tem sessao
 * ativa, a sessao continua "autenticada" com o username antigo, mas
 * {@code UsuarioRepository.findByUsername} nao encontra mais ninguem.</p>
 *
 * <p>Antes disso estourava {@code ResponseStatusException(HttpStatus.
 * UNAUTHORIZED)} direto — que o Spring trata sozinho e devolve um 401 cru
 * (pagina de erro tecnica do navegador), sem nenhuma chance de o usuario
 * simplesmente logar de novo. Este tipo proprio, tratado em
 * {@link GlobalExceptionHandler#handleSessaoInvalida}, invalida a sessao
 * orfa e redireciona para {@code /login} com uma mensagem clara — o mesmo
 * tratamento gracioso que o resto do app ja da a outras excecoes de negocio
 * (ver os demais handlers da mesma classe).</p>
 */
public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException(String message) {
        super(message);
    }
}
