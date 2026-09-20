package br.edu.ifpr.pedidos;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PagamentoService.pagar(total, maxTentativas):
 * - total deve ser positivo; maxTentativas entre 1 e 3 (IllegalArgumentException);
 * - true => aprovado; false => recusa definitiva, sem nova tentativa;
 * - IllegalStateException => indisponibilidade temporária: tenta de novo até o limite;
 * - esgotar as tentativas retorna false; qualquer outra exceção é propagada.
 *
 * O ProcessadorPagamento real não existe: usamos um stub roteirizado que devolve, a cada chamada,
 * o próximo item do roteiro (Boolean ou RuntimeException) e registra os totais recebidos.
 */
class PagamentoServiceTest {

    /** Stub com estado: executa o roteiro na ordem e registra cada chamada. */
    private static final class ProcessadorRoteirizado implements ProcessadorPagamento {
        private final Object[] roteiro;
        final List<Long> totaisRecebidos = new ArrayList<>();

        ProcessadorRoteirizado(Object... roteiro) {
            this.roteiro = roteiro;
        }

        @Override
        public boolean autorizar(long totalCentavos) {
            int chamada = totaisRecebidos.size();
            totaisRecebidos.add(totalCentavos);
            if (chamada >= roteiro.length) {
                throw new AssertionError("Chamada inesperada nº " + (chamada + 1));
            }
            Object passo = roteiro[chamada];
            if (passo instanceof RuntimeException excecao) {
                throw excecao;
            }
            return (Boolean) passo;
        }

        int chamadas() {
            return totaisRecebidos.size();
        }
    }

    private static final IllegalStateException INDISPONIVEL = new IllegalStateException("indisponível");

    // =====================================================================
    // Construção
    // =====================================================================

    @Test
    void deveRejeitarProcessadorNulo() {
        assertThrows(NullPointerException.class, () -> new PagamentoService(null));
    }

    // =====================================================================
    // Validação dos argumentos (o processador nunca deve ser chamado)
    // =====================================================================

    @ParameterizedTest(name = "total {0} deve ser rejeitado")
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void deveRejeitarTotalNaoPositivo(long total) {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado();
        PagamentoService service = new PagamentoService(processador);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> service.pagar(total, 3));

        assertAll(
            () -> assertEquals("Total deve ser positivo", erro.getMessage()),
            () -> assertEquals(0, processador.chamadas())
        );
    }

    @ParameterizedTest(name = "maxTentativas {0} deve ser rejeitado")
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 4, 5, Integer.MAX_VALUE})
    void deveRejeitarLimiteDeTentativasForaDeUmATres(int maxTentativas) {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado();
        PagamentoService service = new PagamentoService(processador);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> service.pagar(1_000, maxTentativas));

        assertAll(
            () -> assertEquals("Use 1 a 3 tentativas", erro.getMessage()),
            () -> assertEquals(0, processador.chamadas())
        );
    }

    @Test
    void deveAceitarOMenorTotalPositivo() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(true);

        assertTrue(new PagamentoService(processador).pagar(1, 1));
        assertEquals(List.of(1L), processador.totaisRecebidos);
    }

    // =====================================================================
    // Aprovação e recusa (uma única passagem pelo laço)
    // =====================================================================

    @ParameterizedTest(name = "aprovação na 1ª tentativa com limite {0}")
    @ValueSource(ints = {1, 2, 3})
    void deveAprovarNaPrimeiraTentativaEChamarOProcessadorUmaUnicaVez(int maxTentativas) {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(true);
        PagamentoService service = new PagamentoService(processador);

        boolean resultado = service.pagar(11_200, maxTentativas);

        assertAll(
            () -> assertTrue(resultado),
            () -> assertEquals(List.of(11_200L), processador.totaisRecebidos)
        );
    }

    @ParameterizedTest(name = "recusa definitiva com limite {0}")
    @ValueSource(ints = {1, 2, 3})
    void naoDeveRepetirQuandoOProcessadorRecusa(int maxTentativas) {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(false);
        PagamentoService service = new PagamentoService(processador);

        boolean resultado = service.pagar(5_000, maxTentativas);

        assertAll(
            () -> assertFalse(resultado),
            () -> assertEquals(List.of(5_000L), processador.totaisRecebidos)
        );
    }

    // =====================================================================
    // Indisponibilidade temporária: uma, duas e três tentativas
    // =====================================================================

    @Test
    void deveAprovarNaSegundaTentativaAposUmaIndisponibilidade() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL, true);
        PagamentoService service = new PagamentoService(processador);

        boolean resultado = service.pagar(7_777, 3);

        assertAll(
            () -> assertTrue(resultado),
            () -> assertEquals(List.of(7_777L, 7_777L), processador.totaisRecebidos)
        );
    }

    @Test
    void deveAprovarNaTerceiraTentativaAposDuasIndisponibilidades() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL, INDISPONIVEL, true);
        PagamentoService service = new PagamentoService(processador);

        boolean resultado = service.pagar(7_777, 3);

        assertAll(
            () -> assertTrue(resultado),
            () -> assertEquals(List.of(7_777L, 7_777L, 7_777L), processador.totaisRecebidos)
        );
    }

    @Test
    void deveRecusarSemRepetirDepoisDeUmaIndisponibilidadeSeguidaDeRecusa() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL, false);
        PagamentoService service = new PagamentoService(processador);

        boolean resultado = service.pagar(3_000, 3);

        assertAll(
            () -> assertFalse(resultado),
            () -> assertEquals(2, processador.chamadas())
        );
    }

    // =====================================================================
    // Esgotamento das tentativas
    // =====================================================================

    @Test
    void deveRetornarFalsoAoEsgotarUmaTentativa() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL);

        assertFalse(new PagamentoService(processador).pagar(1_000, 1));
        assertEquals(1, processador.chamadas());
    }

    @Test
    void deveRetornarFalsoAoEsgotarDuasTentativas() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL, INDISPONIVEL);

        assertFalse(new PagamentoService(processador).pagar(1_000, 2));
        assertEquals(2, processador.chamadas());
    }

    @Test
    void deveRetornarFalsoAoEsgotarTresTentativas() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL, INDISPONIVEL, INDISPONIVEL);

        assertFalse(new PagamentoService(processador).pagar(1_000, 3));
        assertEquals(3, processador.chamadas());
    }

    @Test
    void naoDeveExcederOLimiteMesmoQueASegundaChamadaFosseAprovada() {
        // Limite 1: a segunda tentativa (que aprovaria) nunca ocorre.
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL, true);

        assertFalse(new PagamentoService(processador).pagar(1_000, 1));
        assertEquals(1, processador.chamadas());
    }

    @Test
    void naoDeveExcederLimiteDeDuasTentativasMesmoQueATerceiraFosseAprovada() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(INDISPONIVEL, INDISPONIVEL, true);

        assertFalse(new PagamentoService(processador).pagar(1_000, 2));
        assertEquals(2, processador.chamadas());
    }

    // =====================================================================
    // Outras exceções são propagadas (sem nova tentativa)
    // =====================================================================

    @Test
    void devePropagarExcecaoDiferenteDeIllegalStateNaPrimeiraChamada() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(new RuntimeException("falha grave"));
        PagamentoService service = new PagamentoService(processador);

        RuntimeException erro = assertThrows(RuntimeException.class, () -> service.pagar(1_000, 3));

        assertAll(
            () -> assertEquals("falha grave", erro.getMessage()),
            () -> assertEquals(1, processador.chamadas())
        );
    }

    @Test
    void devePropagarIllegalArgumentExceptionDoProcessadorSemRepetir() {
        ProcessadorRoteirizado processador = new ProcessadorRoteirizado(new IllegalArgumentException("cartão inválido"));
        PagamentoService service = new PagamentoService(processador);

        assertThrows(IllegalArgumentException.class, () -> service.pagar(1_000, 3));
        assertEquals(1, processador.chamadas());
    }

    @Test
    void devePropagarExcecaoDeOutroTipoDepoisDeUmaIndisponibilidade() {
        ProcessadorRoteirizado processador =
            new ProcessadorRoteirizado(INDISPONIVEL, new UnsupportedOperationException("erro"));
        PagamentoService service = new PagamentoService(processador);

        assertThrows(UnsupportedOperationException.class, () -> service.pagar(1_000, 3));
        assertEquals(2, processador.chamadas());
    }

    // =====================================================================
    // Integração com lambda simples (sem stub de estado)
    // =====================================================================

    @Test
    void deveFuncionarComProcessadorDeAprovacaoSempre() {
        assertTrue(new PagamentoService(total -> true).pagar(100, 3));
    }

    @Test
    void deveFuncionarComProcessadorDeRecusaSempre() {
        assertFalse(new PagamentoService(total -> false).pagar(100, 3));
    }

    @Test
    void deveEncaminharOTotalExatoAoProcessador() {
        List<Long> recebidos = new ArrayList<>();
        PagamentoService service = new PagamentoService(total -> {
            recebidos.add(total);
            return true;
        });

        service.pagar(123_456_789L, 2);

        assertEquals(List.of(123_456_789L), recebidos);
    }
}
