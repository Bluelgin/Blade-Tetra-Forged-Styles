import unittest
from plan import load_config, select


class TestSelection(unittest.TestCase):
    def setUp(self):
        self.config = load_config()

    def chosen(self, *paths):
        return set(select(self.config, paths)['suites'])

    def test_docs_are_cheap_but_runtime_books_are_not_docs(self):
        self.assertFalse(select(self.config, ['docs/guides/PATCHOULI_MANUAL.md'])['quick'])
        self.assertFalse(self.chosen('README.md'))
        self.assertEqual({'manual', 'tooltip'}, self.chosen('src/main/resources/assets/blade_tetra/lang/zh_cn.json'))
        self.assertEqual({'manual'}, self.chosen('src/main/resources/assets/blade_tetra/patchouli_books/blade_guide/zh_cn/entries/basics/first_blade.json'))

    def test_single_soul_and_shared_soul_dependencies(self):
        self.assertEqual({'raikiri'}, self.chosen('src/main/java/dev/bladetetra/easteregg/RaikiriCharge.java'))
        self.assertTrue({'raikiri', 'sakura', 'kyouka'} <= self.chosen('src/main/java/dev/bladetetra/easteregg/SoulLegacyState.java'))

    def test_shared_damage_and_storage_expand_coverage(self):
        self.assertTrue({'forging', 'raikiri', 'sakura', 'kyouka'} <= self.chosen('src/main/java/dev/bladetetra/combat/InheritedCombatDamage.java'))
        self.assertEqual(set(self.config['groups']['full']), self.chosen('src/main/java/dev/bladetetra/storage/BladeStackStorage.java'))

    def test_paths_union_and_unknown_deleted_paths_fail_safe(self):
        self.assertTrue({'raikiri', 'custom_style'} <= self.chosen(
            'src/main/java/dev/bladetetra/easteregg/RaikiriCharge.java',
            'src/main/java/dev/bladetetra/customstyle/CustomStyleSpec.java'))
        self.assertEqual(set(self.config['groups']['full']), self.chosen('future/removed-file.java'))

    def test_tests_follow_package_and_mikage_is_not_universal(self):
        self.assertEqual({'raikiri'}, self.chosen('src/test/java/dev/bladetetra/easteregg/RaikiriChargeTest.java'))
        self.assertNotIn('mikage', self.chosen('src/main/resources/assets/blade_tetra/lang/en_us.json'))
        self.assertIn('mikage', self.chosen('src/main/java/dev/bladetetra/challenge/MikageEntity.java'))

    def test_dependency_changes_and_manual_compat_include_addons(self):
        self.assertIn('addon_ingots', self.chosen('build.gradle'))
        self.assertIn('addon_art', select(self.config, [], mode='compat')['suites'])

    def test_detection_failure_and_empty_diff_never_skip_native(self):
        for plan in [select(self.config, []), select(self.config, ['README.md'], fallback='diff unavailable')]:
            self.assertEqual(set(self.config['groups']['full']), set(plan['suites']))


if __name__ == '__main__':
    unittest.main()
