package br.edu.ifpr.pedidos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cliente(vip, bloqueado, comprasAnteriores): o único invariante é
 * comprasAnteriores >= 0 (histórico não negativo).
 */
class ClienteTest {

    @Test
    void deveGuardarOsTresCamposInformados() {
        Cliente cliente = new Cliente(true, false, 7);

        assertAll(
            () -> assertTrue(cliente.vip()),
            () -> assertFalse(cliente.bloqueado()),
            () -> assertEquals(7, cliente.comprasAnteriores())
        );
    }

    @Test
    void deveGuardarClienteBloqueadoComum() {
        Cliente cliente = new Cliente(false, true, 2);

        assertAll(
            () -> assertFalse(cliente.vip()),
            () -> assertTrue(cliente.bloqueado()),
            () -> assertEquals(2, cliente.comprasAnteriores())
        );
    }

    @Test
    void deveAceitarZeroComprasAnterioresComoLimiteInferiorValido() {
        Cliente cliente = assertDoesNotThrow(() -> new Cliente(false, false, 0));

        assertEquals(0, cliente.comprasAnteriores());
    }

    @Test
    void deveAceitarHistoricoMuitoGrande() {
        Cliente cliente = new Cliente(false, false, Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, cliente.comprasAnteriores());
    }

    @ParameterizedTest(name = "historico {0} deve ser rejeitado")
    @ValueSource(ints = {-1, -10, Integer.MIN_VALUE})
    void deveRejeitarHistoricoNegativo(int comprasAnteriores) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> new Cliente(false, false, comprasAnteriores));

        assertEquals("Histórico inválido", erro.getMessage());
    }

    @Test
    void deveRejeitarHistoricoNegativoMesmoParaClienteVipEBloqueado() {
        assertThrows(IllegalArgumentException.class, () -> new Cliente(true, true, -1));
    }

    @Test
    void deveTerIgualdadePorValorPoisEUmRecord() {
        assertEquals(new Cliente(true, false, 3), new Cliente(true, false, 3));
        assertNotEquals(new Cliente(true, false, 3), new Cliente(false, false, 3));
        assertNotEquals(new Cliente(true, false, 3), new Cliente(true, true, 3));
        assertNotEquals(new Cliente(true, false, 3), new Cliente(true, false, 4));
    }
}
