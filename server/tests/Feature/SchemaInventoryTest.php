<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Schema;
use Tests\Support\DataInventory;
use Tests\TestCase;

/**
 * Penjaga tahap 31D: tabel baru tidak boleh terlupa saat hapus akun. Tes ini gagal kalau ada tabel
 * di database yang belum diputuskan nasibnya di tests/Support/DataInventory.php.
 */
class SchemaInventoryTest extends TestCase
{
    use RefreshDatabase;

    private function tables(): array
    {
        return array_map(fn ($t) => substr(strrchr('.'.$t, '.'), 1), Schema::getTableListing());
    }

    public function test_setiap_tabel_sudah_diputuskan_nasibnya_saat_hapus_akun(): void
    {
        $known = array_merge(DataInventory::DELETED_WITH_ACCOUNT, array_keys(DataInventory::NOT_USER_DATA));
        $unknown = array_values(array_diff($this->tables(), $known));

        $this->assertSame(
            [],
            $unknown,
            'Tabel ini belum diputuskan nasibnya saat hapus akun: '.implode(', ', $unknown)
            .'. Tambahkan ke DataInventory (dan ke AccountDeletionService kalau berisi data pengguna).',
        );
    }

    public function test_daftar_tidak_menyebut_tabel_yang_sudah_tidak_ada(): void
    {
        $known = array_merge(DataInventory::DELETED_WITH_ACCOUNT, array_keys(DataInventory::NOT_USER_DATA));
        $stale = array_values(array_diff($known, $this->tables()));

        $this->assertSame([], $stale, 'Tabel ada di DataInventory tapi tidak ada di database: '.implode(', ', $stale));
    }

    public function test_tidak_ada_tabel_di_dua_daftar_sekaligus(): void
    {
        $both = array_intersect(DataInventory::DELETED_WITH_ACCOUNT, array_keys(DataInventory::NOT_USER_DATA));

        $this->assertSame([], array_values($both));
    }

    public function test_setiap_alasan_terisi(): void
    {
        foreach (DataInventory::NOT_USER_DATA as $table => $reason) {
            $this->assertNotSame('', trim($reason), "Tabel $table tanpa alasan.");
        }
    }
}
