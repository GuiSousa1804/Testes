package br.edu.ifpr.boletim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Participacao.calcularPontos: entregou atividade = +2; participou da aula = +1; os pontos se acumulam.
 * São duas decisões independentes (V(G) = 3), mas quatro combinações de entrada.
 */
class ParticipacaoTest {

    // Um teste por combinação: cada um corresponde a um caminho diferente do método.

    @Test
    void deveDarTresPontosQuandoEntregouEParticipou() {
        // if 1 verdadeiro, if 2 verdadeiro.
        Participacao participacao = new Participacao();

        int pontos = participacao.calcularPontos(true, true);

        assertEquals(3, pontos);
    }

    @Test
    void deveDarDoisPontosQuandoSoEntregouAAtividade() {
        // if 1 verdadeiro, if 2 falso.
        Participacao participacao = new Participacao();

        assertEquals(2, participacao.calcularPontos(true, false));
    }

    @Test
    void deveDarUmPontoQuandoSoParticipouDaAula() {
        // if 1 falso, if 2 verdadeiro.
        Participacao participacao = new Participacao();

        assertEquals(1, participacao.calcularPontos(false, true));
    }

    @Test
    void deveDarZeroPontosQuandoNaoEntregouENemParticipou() {
        // if 1 falso, if 2 falso.
        Participacao participacao = new Participacao();

        assertEquals(0, participacao.calcularPontos(false, false));
    }

    // Mesma cobertura em formato de tabela (mostra as quatro combinações de uma vez).
    @ParameterizedTest(name = "entregou={0}, participou={1} -> {2} ponto(s)")
    @CsvSource({
        "true,  true,  3",
        "true,  false, 2",
        "false, true,  1",
        "false, false, 0"
    })
    void deveAcumularPontosConformeAsQuatroCombinacoes(boolean entregou, boolean participou, int esperado) {
        assertEquals(esperado, new Participacao().calcularPontos(entregou, participou));
    }
}
